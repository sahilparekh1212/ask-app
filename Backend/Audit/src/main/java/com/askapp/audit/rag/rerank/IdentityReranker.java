package com.askapp.audit.rag.rerank;

import com.askapp.audit.rag.ScoredChunk;

import java.util.List;

/**
 * No-op reranker: returns the first-stage candidates untouched. Wired when {@code rag.rerank.enabled}
 * is false or RAG is unconfigured, so disabling reranking restores exact single-stage (ADR-0010)
 * behaviour. Because it asks for a candidate pool of exactly {@code topK}, retrieval never fetches a
 * wider pool it won't use.
 */
public class IdentityReranker implements Reranker {

	@Override
	public int candidatePoolSize(int topK) {
		return topK;
	}

	@Override
	public List<ScoredChunk> rerank(String query, List<ScoredChunk> candidates, int topK) {
		if (candidates.size() <= topK) {
			return candidates;
		}
		return List.copyOf(candidates.subList(0, topK));
	}

}
