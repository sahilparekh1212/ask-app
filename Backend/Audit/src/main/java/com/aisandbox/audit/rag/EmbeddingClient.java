package com.aisandbox.audit.rag;

import java.util.List;

/**
 * Turns text into embedding vectors. Documents and queries are embedded through distinct
 * methods because retrieval-tuned models (Voyage included) accept an {@code input_type} hint
 * that measurably improves query→document matching.
 */
public interface EmbeddingClient {

	List<float[]> embedDocuments(List<String> texts);

	float[] embedQuery(String text);

}
