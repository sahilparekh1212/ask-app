package com.askapp.audit.rag;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Configuration for the RAG retrieval feature (vector search over this repo's own docs).
 * Mirrors the posture of {@code AssistantProperties}: the embeddings API key is server-side
 * only (env var {@code VOYAGE_API_KEY}) and, when absent, the feature degrades gracefully —
 * startup indexing is skipped and the MCP search tool reports "not configured" — instead of
 * failing startup. Anthropic doesn't offer an embeddings endpoint; Voyage AI is the provider
 * its docs recommend, hence the default model below.
 */
@Component
public class RagProperties {

	private final boolean enabled;
	private final String apiKey;
	private final String embeddingModel;
	private final int embeddingDimension;
	private final int maxChunkChars;
	private final int defaultTopK;

	public RagProperties(
			@Value("${rag.enabled:true}") boolean enabled,
			@Value("${rag.api-key:}") String apiKey,
			@Value("${rag.embedding-model:voyage-3.5-lite}") String embeddingModel,
			@Value("${rag.embedding-dimension:1024}") int embeddingDimension,
			@Value("${rag.max-chunk-chars:2000}") int maxChunkChars,
			@Value("${rag.default-top-k:5}") int defaultTopK) {
		this.enabled = enabled;
		this.apiKey = apiKey;
		this.embeddingModel = embeddingModel;
		this.embeddingDimension = embeddingDimension;
		this.maxChunkChars = maxChunkChars;
		this.defaultTopK = defaultTopK;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public String getApiKey() {
		return apiKey;
	}

	public String getEmbeddingModel() {
		return embeddingModel;
	}

	/**
	 * Requested embedding width. The pgvector column is fixed at {@code vector(1024)} by the
	 * Liquibase changelog, so any override here must stay 1024 while the pgvector store is
	 * active (the in-memory store has no such constraint).
	 */
	public int getEmbeddingDimension() {
		return embeddingDimension;
	}

	public int getMaxChunkChars() {
		return maxChunkChars;
	}

	public int getDefaultTopK() {
		return defaultTopK;
	}

	/** True when retrieval can actually run: feature on and an embeddings API key present. */
	public boolean isConfigured() {
		return enabled && apiKey != null && !apiKey.isBlank();
	}

}
