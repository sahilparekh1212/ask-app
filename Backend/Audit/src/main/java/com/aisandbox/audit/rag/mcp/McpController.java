package com.aisandbox.audit.rag.mcp;

import com.aisandbox.audit.event.AuditEventPublisher;
import com.aisandbox.audit.rag.RagService;
import com.aisandbox.audit.rag.ScoredChunk;
import com.aisandbox.audit.rag.SourceSummary;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A Model Context Protocol server over the Streamable HTTP transport — the piece that lets
 * any MCP-capable client (Claude Code, Claude Desktop, an IDE) ground its answers in this
 * repo's own docs: {@code claude mcp add --transport http ai-sandbox http://localhost:8083/mcp}.
 *
 * <p>Deliberately hand-rolled rather than pulling in the MCP Java SDK: this server needs
 * only the <em>stateless</em> subset of the spec — single JSON-RPC request in, single JSON
 * response out; no sessions, no server-initiated SSE streams, no sampling — and that subset
 * is small enough that the SDK's session/transport machinery would outweigh the protocol
 * code. The tradeoff and its revisit trigger (needing subscriptions/streaming) are ADR-0010.
 *
 * <p>Methods implemented per the 2025-06-18 spec: {@code initialize} (version negotiation +
 * capability advertisement), {@code ping}, {@code tools/list}, {@code tools/call}, and
 * notification acknowledgement (202, no body). A GET is answered 405 — the spec's signal
 * that this server offers no server-initiated stream.
 */
@RestController
public class McpController {

	static final String SERVER_NAME = "ai-sandbox-rag";
	static final String LATEST_PROTOCOL_VERSION = "2025-06-18";
	static final Set<String> SUPPORTED_PROTOCOL_VERSIONS =
		Set.of("2024-11-05", "2025-03-26", LATEST_PROTOCOL_VERSION);

	static final String TOOL_SEARCH = "search_knowledge";
	static final String TOOL_SOURCES = "list_sources";

	private static final Logger log = LoggerFactory.getLogger(McpController.class);

	private final RagService ragService;
	private final ObjectMapper objectMapper;
	private final AuditEventPublisher auditEventPublisher;

	public McpController(RagService ragService, ObjectMapper objectMapper,
			AuditEventPublisher auditEventPublisher) {
		this.ragService = ragService;
		this.objectMapper = objectMapper;
		this.auditEventPublisher = auditEventPublisher;
	}

	/** Spec: a server that offers no server-initiated SSE stream answers GET with 405. */
	@GetMapping("/mcp")
	public ResponseEntity<Void> get() {
		return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).build();
	}

	@PostMapping(value = "/mcp", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Object> post(@RequestBody String body) {
		JsonNode request;
		try {
			request = objectMapper.readTree(body);
		} catch (JsonProcessingException e) {
			return ResponseEntity.ok(error(null, -32700, "Parse error"));
		}
		if (!request.isObject()) {
			// JSON-RPC batching was removed from the MCP spec in 2025-06-18; unsupported here.
			return ResponseEntity.ok(error(null, -32600, "Expected a single JSON-RPC request object"));
		}
		JsonNode id = request.get("id");
		String method = request.path("method").asText("");
		if (id == null || id.isNull()) {
			// A notification (initialized, cancelled, ...): acknowledge, nothing to return.
			log.debug("MCP notification: {}", method);
			return ResponseEntity.accepted().build();
		}
		JsonNode params = request.path("params");
		return ResponseEntity.ok(switch (method) {
			case "initialize" -> result(id, initialize(params));
			case "ping" -> result(id, Map.of());
			case "tools/list" -> result(id, Map.of("tools", toolDefinitions()));
			case "tools/call" -> toolsCall(id, params);
			default -> error(id, -32601, "Method not found: " + method);
		});
	}

	private Map<String, Object> initialize(JsonNode params) {
		String requested = params.path("protocolVersion").asText("");
		String negotiated = SUPPORTED_PROTOCOL_VERSIONS.contains(requested)
			? requested
			: LATEST_PROTOCOL_VERSION;
		return Map.of(
			"protocolVersion", negotiated,
			"capabilities", Map.of("tools", Map.of()),
			"serverInfo", Map.of(
				"name", SERVER_NAME,
				"title", "AI-Sandbox repo knowledge (RAG)",
				"version", "0.0.1"),
			"instructions", "Retrieval over the AI-Sandbox repository's own documentation "
				+ "(README, ADRs, docs/). Use search_knowledge to ground answers about this "
				+ "project's architecture and decisions; use list_sources to see what is indexed.");
	}

	private List<Map<String, Object>> toolDefinitions() {
		Map<String, Object> searchSchema = Map.of(
			"type", "object",
			"properties", Map.of(
				"query", Map.of(
					"type", "string",
					"description", "Natural-language question about the AI-Sandbox project"),
				"top_k", Map.of(
					"type", "integer",
					"description", "How many chunks to return (1-20, default 5)")),
			"required", List.of("query"));
		Map<String, Object> sourcesSchema = Map.of("type", "object", "properties", Map.of());
		return List.of(
			Map.of(
				"name", TOOL_SEARCH,
				"description", "Semantic search over this repository's documentation (README, "
					+ "ADRs, deployment guide). Returns the most relevant doc chunks with their "
					+ "source file, heading and similarity score.",
				"inputSchema", searchSchema),
			Map.of(
				"name", TOOL_SOURCES,
				"description", "Lists the documents currently in the RAG index and how many "
					+ "chunks each contributed.",
				"inputSchema", sourcesSchema));
	}

	private Map<String, Object> toolsCall(JsonNode id, JsonNode params) {
		String tool = params.path("name").asText("");
		JsonNode arguments = params.path("arguments");
		long startNanos = System.nanoTime();
		try {
			Map<String, Object> toolResult = switch (tool) {
				case TOOL_SEARCH -> searchKnowledge(arguments);
				case TOOL_SOURCES -> listSources();
				// Spec: an unknown tool is a protocol-level error, not a tool-result error.
				default -> null;
			};
			if (toolResult == null) {
				return error(id, -32602, "Unknown tool: " + tool);
			}
			auditToolCall(tool, toolResult, startNanos);
			return result(id, toolResult);
		} catch (IllegalArgumentException e) {
			return error(id, -32602, e.getMessage());
		} catch (RuntimeException e) {
			// Execution failure (provider outage, store error): the spec wants these inside
			// the result with isError so the LLM can see and react to the failure.
			log.error("MCP tool '{}' failed: {}", tool, e.getClass().getSimpleName());
			return result(id, toolError("Tool '" + tool + "' failed: " + e.getMessage()));
		}
	}

	/**
	 * Emit a domain event for a completed tool call. {@code search_knowledge} is fundamentally a
	 * semantic search, so it maps to {@code Rag/SEARCH}; every other tool maps to the generic
	 * {@code Mcp/TOOL_CALL}. Non-PII detail only — the tool name, latency, and whether the tool
	 * returned an error result, never the query text or the retrieved content.
	 */
	private void auditToolCall(String tool, Map<String, Object> toolResult, long startNanos) {
		long latencyMs = (System.nanoTime() - startNanos) / 1_000_000;
		boolean isError = Boolean.TRUE.equals(toolResult.get("isError"));
		String details = "tool=" + tool + " latencyMs=" + latencyMs + " error=" + isError;
		if (TOOL_SEARCH.equals(tool)) {
			auditEventPublisher.publish("Rag", "SEARCH", details);
		} else {
			auditEventPublisher.publish("Mcp", "TOOL_CALL", details);
		}
	}

	private Map<String, Object> searchKnowledge(JsonNode arguments) {
		if (!ragService.isConfigured()) {
			return toolError("RAG is not configured on this server (missing VOYAGE_API_KEY); "
				+ "the index is unavailable.");
		}
		String query = arguments.path("query").asText("");
		if (query.isBlank()) {
			throw new IllegalArgumentException("'query' is required and must be a non-empty string");
		}
		Integer topK = arguments.hasNonNull("top_k") ? arguments.get("top_k").asInt() : null;
		List<ScoredChunk> chunks = ragService.search(query, topK);
		if (chunks.isEmpty()) {
			return toolText("No matching documentation found (the index may be empty).");
		}
		List<Map<String, Object>> content = new java.util.ArrayList<>();
		for (int i = 0; i < chunks.size(); i++) {
			ScoredChunk chunk = chunks.get(i);
			content.add(textBlock(String.format("[%d] %s — \"%s\" (score %.3f)%n%s",
				i + 1, chunk.source(), chunk.heading(), chunk.score(), chunk.content())));
		}
		return Map.of("content", content, "isError", false);
	}

	private Map<String, Object> listSources() {
		List<SourceSummary> sources = ragService.sources();
		if (sources.isEmpty()) {
			return toolText("The RAG index is empty"
				+ (ragService.isConfigured() ? "." : " (RAG is not configured on this server)."));
		}
		StringBuilder sb = new StringBuilder("Indexed documents (" + ragService.chunkCount() + " chunks):\n");
		for (SourceSummary source : sources) {
			sb.append("- ").append(source.source()).append(" (").append(source.chunkCount())
				.append(source.chunkCount() == 1 ? " chunk" : " chunks").append(")\n");
		}
		return toolText(sb.toString());
	}

	private static Map<String, Object> toolText(String text) {
		return Map.of("content", List.of(textBlock(text)), "isError", false);
	}

	private static Map<String, Object> toolError(String text) {
		return Map.of("content", List.of(textBlock(text)), "isError", true);
	}

	private static Map<String, Object> textBlock(String text) {
		return Map.of("type", "text", "text", text);
	}

	private static Map<String, Object> result(JsonNode id, Object result) {
		Map<String, Object> envelope = new LinkedHashMap<>();
		envelope.put("jsonrpc", "2.0");
		envelope.put("id", id);
		envelope.put("result", result);
		return envelope;
	}

	private static Map<String, Object> error(JsonNode id, int code, String message) {
		Map<String, Object> envelope = new LinkedHashMap<>();
		envelope.put("jsonrpc", "2.0");
		envelope.put("id", id);
		envelope.put("error", Map.of("code", code, "message", message));
		return envelope;
	}

}
