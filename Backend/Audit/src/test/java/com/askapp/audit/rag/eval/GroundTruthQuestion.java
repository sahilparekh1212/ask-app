package com.askapp.audit.rag.eval;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * One labelled RAG-eval question (ADR-0013): a natural-language question and the corpus source(s)
 * that should be retrieved to answer it. {@code expectedSources} are path <em>suffixes</em> matched
 * against a retrieved chunk's source, so they survive package/path renames (see
 * {@link RagEvalMetrics#matches}). Loaded from {@code rag-eval/ground-truth.jsonl}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GroundTruthQuestion(String id, String question, List<String> expectedSources, List<String> tags) {
}
