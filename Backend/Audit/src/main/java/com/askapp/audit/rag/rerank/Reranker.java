package com.askapp.audit.rag.rerank;

import com.askapp.audit.rag.ScoredChunk;

import java.util.List;

/**
 * Second stage of two-stage retrieval (ADR-0012): re-scores the first-stage cosine candidates
 * against the query with a cross-encoder and keeps the best {@code topK}. Two implementations —
 * {@code VoyageReranker} (a real reranker call) and {@code IdentityReranker} (a no-op passthrough
 * used when reranking is off or RAG is unconfigured) — are selected once at startup by
 * {@code RerankConfiguration}.
 */
public interface Reranker {

	/**
	 * How many first-stage (cosine) candidates {@code RagService} should fetch before reranking to
	 * {@code topK}. A real reranker asks for a wider pool than {@code topK}; the identity reranker
	 * asks for exactly {@code topK} so nothing is embedded or fetched that won't be used.
	 */
	int candidatePoolSize(int topK);

	/**
	 * Reorder {@code candidates} by relevance to {@code query} and return the best {@code topK}.
	 * Implementations are fail-soft: a reranking failure falls back to the given (cosine) order
	 * rather than propagating, because reranking is an enhancement, not a dependency (ADR-0012).
	 */
	List<ScoredChunk> rerank(String query, List<ScoredChunk> candidates, int topK);

}
