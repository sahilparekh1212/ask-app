package com.askapp.audit.rag;

import org.springframework.stereotype.Service;

import java.util.List;

/**
 * The retrieval facade both consumers share: the MCP server's {@code search_knowledge} tool
 * and the chat assistant's grounding context. Query text is embedded with the provider's
 * {@code query} input type and matched against document chunks by cosine similarity.
 */
@Service
public class RagService {

	static final int MAX_TOP_K = 20;

	private final RagProperties properties;
	private final EmbeddingClient embeddingClient;
	private final VectorStore vectorStore;

	public RagService(RagProperties properties, EmbeddingClient embeddingClient, VectorStore vectorStore) {
		this.properties = properties;
		this.embeddingClient = embeddingClient;
		this.vectorStore = vectorStore;
	}

	public boolean isConfigured() {
		return properties.isConfigured();
	}

	/** True when retrieval can return something useful: configured and index non-empty. */
	public boolean isReady() {
		return isConfigured() && vectorStore.count() > 0;
	}

	public List<ScoredChunk> search(String query, Integer topK) {
		if (!isConfigured()) {
			throw new IllegalStateException("RAG is not configured (missing VOYAGE_API_KEY)");
		}
		int k = topK == null ? properties.getDefaultTopK() : Math.min(Math.max(topK, 1), MAX_TOP_K);
		return vectorStore.search(embeddingClient.embedQuery(query), k);
	}

	public List<SourceSummary> sources() {
		return vectorStore.sources();
	}

	public long chunkCount() {
		return vectorStore.count();
	}

}
