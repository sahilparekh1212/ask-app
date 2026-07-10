package com.aisandbox.audit.rag;

/** One indexed document and how many chunks it contributed to the vector store. */
public record SourceSummary(String source, long chunkCount) {
}
