package com.askapp.audit.rag;

import com.askapp.audit.rag.rerank.Reranker;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * The retrieval facade both consumers share: the MCP server's {@code search_knowledge} tool
 * and the chat assistant's grounding context. Retrieval is two-stage (ADR-0012): the query is
 * embedded with the provider's {@code query} input type, a wider candidate pool is matched by
 * cosine similarity, and a {@link Reranker} re-scores that pool against the query and keeps the
 * best top-k. With reranking off (the identity reranker) the pool collapses to exactly top-k, so
 * this behaves as single-stage cosine retrieval.
 */
@Service
public class RagService {

	static final int MAX_TOP_K = 20;
	/** Hard ceiling on the first-stage candidate pool, so {@code rag.rerank.candidate-pool-size}
	 * (or a rogue reranker) can't make retrieval fetch an unbounded number of chunks. */
	static final int MAX_CANDIDATE_POOL = 50;

	private final RagProperties properties;
	private final EmbeddingClient embeddingClient;
	private final VectorStore vectorStore;
	private final Reranker reranker;

	public RagService(RagProperties properties, EmbeddingClient embeddingClient, VectorStore vectorStore,
			Reranker reranker) {
		this.properties = properties;
		this.embeddingClient = embeddingClient;
		this.vectorStore = vectorStore;
		this.reranker = reranker;
	}

	public boolean isConfigured() {
		return properties.isConfigured();
	}

	/** True when retrieval can return something useful: configured and index non-empty. */
	public boolean isReady() {
		return isConfigured() && vectorStore.count() > 0;
	}

	public List<ScoredChunk> search(String query, Integer topK) {
		return retrieve(query, topK).results();
	}

	/**
	 * Both stages of retrieval for one query: the reranked {@code results} (what grounds the answer)
	 * and the pre-rerank {@code candidates} pool they were selected from. {@code search} returns only
	 * the results; tracing (ADR-0014) uses {@code retrieve} so it can record how the reranker
	 * reordered the pool.
	 */
	public Retrieval retrieve(String query, Integer topK) {
		if (!isConfigured()) {
			throw new IllegalStateException("RAG is not configured (missing VOYAGE_API_KEY)");
		}
		int k = topK == null ? properties.getDefaultTopK() : Math.min(Math.max(topK, 1), MAX_TOP_K);
		int poolSize = Math.min(reranker.candidatePoolSize(k), MAX_CANDIDATE_POOL);
		List<ScoredChunk> candidates = vectorStore.search(embeddingClient.embedQuery(query), poolSize);
		List<ScoredChunk> results = reranker.rerank(query, candidates, k);
		return new Retrieval(results, candidates);
	}

	/** The two stages of a retrieval: post-rerank {@code results} and the pre-rerank {@code candidates}. */
	public record Retrieval(List<ScoredChunk> results, List<ScoredChunk> candidates) {
	}

	public List<SourceSummary> sources() {
		return vectorStore.sources();
	}

	public long chunkCount() {
		return vectorStore.count();
	}

}
