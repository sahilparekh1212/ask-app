package com.askapp.audit.rag;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Brute-force cosine-similarity store, the default {@code rag.vector-store}. Active for
 * LOCAL and tests, where the datasource is H2 and the pgvector extension can't exist. Exact
 * (not approximate) — at a few hundred chunks a linear scan is microseconds, so reaching for
 * an ANN index here would be pure ceremony.
 */
@Component
@ConditionalOnProperty(name = "rag.vector-store", havingValue = "memory", matchIfMissing = true)
public class InMemoryVectorStore implements VectorStore {

	private record Stored(DocChunk chunk, float[] vector) {
	}

	private final Map<String, Stored> byId = new ConcurrentHashMap<>();

	@Override
	public Set<String> existingIds() {
		return new HashSet<>(byId.keySet());
	}

	@Override
	public void upsert(List<DocChunk> chunks, List<float[]> vectors) {
		if (chunks.size() != vectors.size()) {
			throw new IllegalArgumentException("chunks and vectors must be the same size");
		}
		for (int i = 0; i < chunks.size(); i++) {
			byId.putIfAbsent(chunks.get(i).id(), new Stored(chunks.get(i), vectors.get(i)));
		}
	}

	@Override
	public void retainOnly(Set<String> liveIds) {
		byId.keySet().removeIf(id -> !liveIds.contains(id));
	}

	@Override
	public List<ScoredChunk> search(float[] query, int topK) {
		return byId.values().stream()
			.map(stored -> new ScoredChunk(stored.chunk().source(), stored.chunk().heading(),
				stored.chunk().content(), cosine(query, stored.vector())))
			.sorted(Comparator.comparingDouble(ScoredChunk::score).reversed())
			.limit(topK)
			.toList();
	}

	@Override
	public List<SourceSummary> sources() {
		return byId.values().stream()
			.collect(java.util.stream.Collectors.groupingBy(stored -> stored.chunk().source(),
				java.util.stream.Collectors.counting()))
			.entrySet().stream()
			.map(entry -> new SourceSummary(entry.getKey(), entry.getValue()))
			.sorted(Comparator.comparing(SourceSummary::source))
			.toList();
	}

	@Override
	public long count() {
		return byId.size();
	}

	static double cosine(float[] a, float[] b) {
		if (a.length != b.length) {
			throw new IllegalArgumentException("vector dimensions differ: " + a.length + " vs " + b.length);
		}
		double dot = 0;
		double normA = 0;
		double normB = 0;
		for (int i = 0; i < a.length; i++) {
			dot += (double) a[i] * b[i];
			normA += (double) a[i] * a[i];
			normB += (double) b[i] * b[i];
		}
		if (normA == 0 || normB == 0) {
			return 0;
		}
		return dot / (Math.sqrt(normA) * Math.sqrt(normB));
	}

}
