package com.askapp.audit.rag;

/**
 * A retrieved chunk with its score: cosine similarity from the vector store (1.0 = identical
 * direction), or — after reranking (ADR-0012) — the reranker's relevance score. Higher is better
 * either way; the scale differs, so scores are for ordering, not cross-stage comparison.
 */
public record ScoredChunk(String source, String heading, String content, double score) {
}
