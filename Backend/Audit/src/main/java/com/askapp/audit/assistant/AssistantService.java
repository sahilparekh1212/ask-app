package com.askapp.audit.assistant;

import com.askapp.audit.assistant.dto.ChatRequest;
import com.askapp.audit.assistant.dto.ChatResponse;
import com.askapp.audit.event.AuditEventPublisher;
import com.askapp.audit.rag.RagService;
import com.askapp.audit.rag.ScoredChunk;
import com.askapp.audit.trace.AiMetrics;
import com.askapp.audit.trace.AiTraceRecord;
import com.askapp.audit.trace.AiTraceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Orchestrates one chat turn: screen the input, assemble the role-scoped context, call the
 * provider. The order matters — screening runs first, so a request carrying a token, a
 * credential, or PII is answered locally and never reaches the LLM provider at all.
 *
 * <p>Every turn is recorded to the AI trace (ADR-0014) for answer-quality analysis — the query,
 * retrieval (both rerank stages), model, reply, latencies and tokens — and to Micrometer for
 * Grafana. Tracing is best-effort and asynchronous, so it never adds latency to or breaks a turn.
 */
@Service
public class AssistantService {

	static final String BLOCKED_REPLY = "I can't help with messages containing credentials, "
		+ "tokens, or personal data (like emails or card numbers). Please remove the sensitive "
		+ "value and ask again.";

	private static final RagService.Retrieval NO_RETRIEVAL = new RagService.Retrieval(List.of(), List.of());

	private static final Logger log = LoggerFactory.getLogger(AssistantService.class);

	private final AssistantProperties properties;
	private final PromptScreener screener;
	private final AssistantContextBuilder contextBuilder;
	private final AuditQueryDetector auditQueryDetector;
	private final LlmClient llmClient;
	private final RagService ragService;
	private final AuditEventPublisher auditEventPublisher;
	private final AiTraceService aiTraceService;
	private final AiMetrics aiMetrics;

	public AssistantService(AssistantProperties properties, PromptScreener screener,
			AssistantContextBuilder contextBuilder, AuditQueryDetector auditQueryDetector,
			LlmClient llmClient, RagService ragService, AuditEventPublisher auditEventPublisher,
			AiTraceService aiTraceService, AiMetrics aiMetrics) {
		this.properties = properties;
		this.screener = screener;
		this.contextBuilder = contextBuilder;
		this.auditQueryDetector = auditQueryDetector;
		this.llmClient = llmClient;
		this.ragService = ragService;
		this.auditEventPublisher = auditEventPublisher;
		this.aiTraceService = aiTraceService;
		this.aiMetrics = aiMetrics;
	}

	public ChatResponse chat(ChatRequest request, boolean admin) {
		if (!properties.isConfigured()) {
			throw new AssistantUnavailableException(
				"Assistant is not configured on this server (missing ANTHROPIC_API_KEY)");
		}
		Optional<String> violation = screener.firstViolation(request);
		if (violation.isPresent()) {
			// Log the category only — never the matched content (that would move the
			// sensitive value into our logs instead of the provider's).
			log.info("Assistant request blocked by screen category={} messageLength={}",
				violation.get(), request.message().length());
			// Emit the blocked turn too: the screener category is a rule name, not content,
			// so a blocked-rate is visible on the dashboard without leaking the matched value.
			auditEventPublisher.publish("Assistant", "CHAT",
				"blocked=true category=" + violation.get());
			aiTraceService.record(AiTraceRecord.blockedChat(request.message(), admin, violation.get()));
			aiMetrics.recordBlockedChat(violation.get());
			return ChatResponse.blockedBy(BLOCKED_REPLY);
		}
		long retrievalStart = System.nanoTime();
		RagService.Retrieval retrieval = retrieve(request.message());
		long retrievalMs = (System.nanoTime() - retrievalStart) / 1_000_000;
		List<ScoredChunk> retrieved = retrieval.results();
		aiMetrics.recordRetrieval("chat", retrievalMs, retrieval);
		// Only attach the live audit stats/rows when the question is actually about the running
		// system's state — a generic design/architecture question stays docs-only.
		boolean includeAuditData = auditQueryDetector.isAboutState(request.message());
		long startNanos = System.nanoTime();
		try {
			LlmResult result = llmClient.complete(
				contextBuilder.buildSystemPrompt(admin, retrieved, includeAuditData),
				request.historyOrEmpty(), request.message());
			long latencyMs = (System.nanoTime() - startNanos) / 1_000_000;
			String reply = result.text();
			log.info("Assistant replied admin={} messageLength={} replyLength={}",
				admin, request.message().length(), reply.length());
			// Non-PII detail only: model, latency, RAG grounding used, and whether live audit
			// data was attached — never the message or the reply text.
			auditEventPublisher.publish("Assistant", "CHAT", "blocked=false model="
				+ properties.getModel() + " latencyMs=" + latencyMs + " retrievedChunks="
				+ retrieved.size() + " auditGrounded=" + includeAuditData);
			// The full trace (content, rankings, tokens) goes to the separate ai_trace table.
			aiTraceService.record(AiTraceRecord.chat(request.message(), retrieval, properties.getModel(),
				reply, result, retrievalMs, latencyMs, admin, includeAuditData));
			aiMetrics.recordChat(latencyMs);
			return ChatResponse.of(reply);
		} catch (RuntimeException e) {
			log.error("Assistant provider call failed: {}", e.getClass().getSimpleName());
			throw new AssistantUnavailableException("Assistant is temporarily unavailable", e);
		}
	}

	/**
	 * RAG grounding for the question (see the rag/ package): best-effort by design. Runs
	 * only after the screener passed the message (a blocked message never reaches the
	 * embeddings provider either), and any retrieval failure degrades to an empty result
	 * (the pre-RAG prompt) rather than failing the chat — retrieval is an enhancement, not a
	 * dependency. Returns both rerank stages so the trace can record how retrieval was reordered.
	 */
	private RagService.Retrieval retrieve(String message) {
		if (!ragService.isReady()) {
			return NO_RETRIEVAL;
		}
		try {
			return ragService.retrieve(message, null);
		} catch (RuntimeException e) {
			log.warn("RAG retrieval failed, continuing without retrieved context: {}",
				e.getClass().getSimpleName());
			return NO_RETRIEVAL;
		}
	}

}
