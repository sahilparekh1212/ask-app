package com.askapp.audit.trace;

import com.askapp.audit.assistant.LlmResult;
import com.askapp.audit.rag.RagService;

/**
 * The immutable snapshot of one AI interaction, built on the request thread and handed to
 * {@link AiTraceService} to persist asynchronously (ADR-0014). Factories cover the three shapes:
 * a completed chat turn, a blocked chat turn, and an MCP search.
 */
public record AiTraceRecord(
	String feature,
	String query,
	RagService.Retrieval retrieval,
	String model,
	String reply,
	Integer inputTokens,
	Integer outputTokens,
	Long retrievalMs,
	Long llmMs,
	boolean blocked,
	String blockedCategory,
	boolean admin,
	boolean auditGrounded) {

	public static final String CHAT = "CHAT";
	public static final String MCP_SEARCH = "MCP_SEARCH";

	/** A completed chat turn: retrieval (both stages), the model reply, latencies and token usage. */
	public static AiTraceRecord chat(String query, RagService.Retrieval retrieval, String model, String reply,
			LlmResult result, long retrievalMs, long llmMs, boolean admin, boolean auditGrounded) {
		return new AiTraceRecord(CHAT, query, retrieval, model, reply,
			result.inputTokens(), result.outputTokens(), retrievalMs, llmMs, false, null, admin, auditGrounded);
	}

	/** A chat turn stopped by the prompt screener: only the query and the screener category. */
	public static AiTraceRecord blockedChat(String query, boolean admin, String category) {
		return new AiTraceRecord(CHAT, query, null, null, null, null, null, null, null, true, category, admin, false);
	}

	/** An MCP {@code search_knowledge} call: the query, retrieval (both stages) and its latency. */
	public static AiTraceRecord mcpSearch(String query, RagService.Retrieval retrieval, long retrievalMs) {
		return new AiTraceRecord(MCP_SEARCH, query, retrieval, null, null, null, null, retrievalMs, null,
			false, null, false, false);
	}

}
