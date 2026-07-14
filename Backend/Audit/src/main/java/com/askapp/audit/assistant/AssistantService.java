package com.askapp.audit.assistant;

import com.askapp.audit.assistant.dto.ChatRequest;
import com.askapp.audit.assistant.dto.ChatResponse;
import com.askapp.audit.event.AuditEventPublisher;
import com.askapp.audit.rag.RagService;
import com.askapp.audit.rag.ScoredChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Orchestrates one chat turn: screen the input, assemble the role-scoped context, call the
 * provider. The order matters — screening runs first, so a request carrying a token, a
 * credential, or PII is answered locally and never reaches the LLM provider at all.
 */
@Service
public class AssistantService {

	static final String BLOCKED_REPLY = "I can't help with messages containing credentials, "
		+ "tokens, or personal data (like emails or card numbers). Please remove the sensitive "
		+ "value and ask again.";

	private static final Logger log = LoggerFactory.getLogger(AssistantService.class);

	private final AssistantProperties properties;
	private final PromptScreener screener;
	private final AssistantContextBuilder contextBuilder;
	private final AuditQueryDetector auditQueryDetector;
	private final LlmClient llmClient;
	private final RagService ragService;
	private final AuditEventPublisher auditEventPublisher;

	public AssistantService(AssistantProperties properties, PromptScreener screener,
			AssistantContextBuilder contextBuilder, AuditQueryDetector auditQueryDetector,
			LlmClient llmClient, RagService ragService, AuditEventPublisher auditEventPublisher) {
		this.properties = properties;
		this.screener = screener;
		this.contextBuilder = contextBuilder;
		this.auditQueryDetector = auditQueryDetector;
		this.llmClient = llmClient;
		this.ragService = ragService;
		this.auditEventPublisher = auditEventPublisher;
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
			return ChatResponse.blockedBy(BLOCKED_REPLY);
		}
		List<ScoredChunk> retrieved = retrieve(request.message());
		// Only attach the live audit stats/rows when the question is actually about the running
		// system's state — a generic design/architecture question stays docs-only.
		boolean includeAuditData = auditQueryDetector.isAboutState(request.message());
		long startNanos = System.nanoTime();
		try {
			String reply = llmClient.complete(
				contextBuilder.buildSystemPrompt(admin, retrieved, includeAuditData),
				request.historyOrEmpty(), request.message());
			long latencyMs = (System.nanoTime() - startNanos) / 1_000_000;
			log.info("Assistant replied admin={} messageLength={} replyLength={}",
				admin, request.message().length(), reply.length());
			// Non-PII detail only: model, latency, RAG grounding used, and whether live audit
			// data was attached — never the message or the reply text.
			auditEventPublisher.publish("Assistant", "CHAT", "blocked=false model="
				+ properties.getModel() + " latencyMs=" + latencyMs + " retrievedChunks="
				+ retrieved.size() + " auditGrounded=" + includeAuditData);
			return ChatResponse.of(reply);
		} catch (RuntimeException e) {
			log.error("Assistant provider call failed: {}", e.getClass().getSimpleName());
			throw new AssistantUnavailableException("Assistant is temporarily unavailable", e);
		}
	}

	/**
	 * RAG grounding for the question (see the rag/ package): best-effort by design. Runs
	 * only after the screener passed the message (a blocked message never reaches the
	 * embeddings provider either), and any retrieval failure degrades to the pre-RAG prompt
	 * rather than failing the chat — retrieval is an enhancement, not a dependency.
	 */
	private List<ScoredChunk> retrieve(String message) {
		if (!ragService.isReady()) {
			return List.of();
		}
		try {
			return ragService.search(message, null);
		} catch (RuntimeException e) {
			log.warn("RAG retrieval failed, continuing without retrieved context: {}",
				e.getClass().getSimpleName());
			return List.of();
		}
	}

}
