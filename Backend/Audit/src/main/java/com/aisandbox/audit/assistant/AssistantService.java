package com.aisandbox.audit.assistant;

import com.aisandbox.audit.assistant.dto.ChatRequest;
import com.aisandbox.audit.assistant.dto.ChatResponse;
import com.aisandbox.audit.rag.RagService;
import com.aisandbox.audit.rag.ScoredChunk;
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
	private final LlmClient llmClient;
	private final RagService ragService;

	public AssistantService(AssistantProperties properties, PromptScreener screener,
			AssistantContextBuilder contextBuilder, LlmClient llmClient, RagService ragService) {
		this.properties = properties;
		this.screener = screener;
		this.contextBuilder = contextBuilder;
		this.llmClient = llmClient;
		this.ragService = ragService;
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
			return ChatResponse.blockedBy(BLOCKED_REPLY);
		}
		try {
			String reply = llmClient.complete(
				contextBuilder.buildSystemPrompt(admin, retrieve(request.message())),
				request.historyOrEmpty(), request.message());
			log.info("Assistant replied admin={} messageLength={} replyLength={}",
				admin, request.message().length(), reply.length());
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
