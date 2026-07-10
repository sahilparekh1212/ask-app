package com.aisandbox.audit.rag;

/** A retrieved chunk with its cosine-similarity score (1.0 = identical direction). */
public record ScoredChunk(String source, String heading, String content, double score) {
}
