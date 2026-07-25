package com.askapp.audit.rag.rerank;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Configuration for the second retrieval stage (reranking — see ADR-0012). Reranking reuses the
 * embeddings provider's account, so there is no separate key here: {@code VOYAGE_API_KEY} (held by
 * {@code RagProperties}) enables both stages. When {@code enabled} is false the identity reranker is
 * wired instead and retrieval behaves exactly as it did before ADR-0012.
 */
@Component
public class RerankProperties {

	private final boolean enabled;
	private final String model;
	private final int candidatePoolSize;

	public RerankProperties(
			@Value("${rag.rerank.enabled:true}") boolean enabled,
			@Value("${rag.rerank.model:rerank-2.5-lite}") String model,
			@Value("${rag.rerank.candidate-pool-size:20}") int candidatePoolSize) {
		this.enabled = enabled;
		this.model = model;
		this.candidatePoolSize = candidatePoolSize;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public String getModel() {
		return model;
	}

	/**
	 * How many first-stage cosine candidates to recall before reranking. Wider than the returned
	 * top-k so the reranker has something to reorder; {@code RagService} caps it so an over-large
	 * value can't make retrieval fetch an unbounded number of chunks.
	 */
	public int getCandidatePoolSize() {
		return candidatePoolSize;
	}

}
