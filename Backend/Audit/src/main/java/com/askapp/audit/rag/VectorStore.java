package com.askapp.audit.rag;

import java.util.List;
import java.util.Set;

/**
 * Storage + similarity search for embedded chunks. Two implementations exist behind the
 * {@code rag.vector-store} property: {@code pgvector} (the real vector database, used by the
 * Docker/DEV+ stack where Postgres runs) and {@code memory} (brute-force cosine, the default
 * for LOCAL/tests where the datasource is H2 — pgvector is a Postgres extension and doesn't
 * exist there). The corpus is a few hundred chunks, so the in-memory store is exact, not an
 * approximation.
 */
public interface VectorStore {

	/** Ids already stored — the indexer skips embedding chunks whose content hash is present. */
	Set<String> existingIds();

	/** Stores chunks with their vectors; already-present ids are ignored (idempotent). */
	void upsert(List<DocChunk> chunks, List<float[]> vectors);

	/** Deletes every stored chunk whose id is not in {@code liveIds} (stale-content sweep). */
	void retainOnly(Set<String> liveIds);

	/** Top-{@code topK} chunks by cosine similarity to {@code query}, best first. */
	List<ScoredChunk> search(float[] query, int topK);

	/** Indexed documents with chunk counts, ordered by source name. */
	List<SourceSummary> sources();

	long count();

}
