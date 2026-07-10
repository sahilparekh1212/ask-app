package com.aisandbox.audit.rag;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class InMemoryVectorStoreTest {

	private final InMemoryVectorStore store = new InMemoryVectorStore();

	private static DocChunk chunk(String source, String content) {
		return DocChunk.of(source, "H", content);
	}

	@Test
	void searchRanksByCosineSimilarity() {
		DocChunk close = chunk("a.md", "close");
		DocChunk far = chunk("b.md", "far");
		store.upsert(List.of(close, far), List.of(new float[] {1, 0}, new float[] {0, 1}));

		List<ScoredChunk> results = store.search(new float[] {1, 0.1f}, 2);

		assertThat(results).hasSize(2);
		assertThat(results.get(0).content()).isEqualTo("close");
		assertThat(results.get(0).score()).isGreaterThan(results.get(1).score());
	}

	@Test
	void searchHonoursTopK() {
		store.upsert(List.of(chunk("a.md", "one"), chunk("a.md", "two"), chunk("a.md", "three")),
			List.of(new float[] {1, 0}, new float[] {0, 1}, new float[] {1, 1}));

		assertThat(store.search(new float[] {1, 0}, 2)).hasSize(2);
	}

	@Test
	void upsertIsIdempotentPerContentHash() {
		DocChunk chunk = chunk("a.md", "same");
		store.upsert(List.of(chunk), List.of(new float[] {1, 0}));
		store.upsert(List.of(chunk), List.of(new float[] {1, 0}));

		assertThat(store.count()).isEqualTo(1);
		assertThat(store.existingIds()).containsExactly(chunk.id());
	}

	@Test
	void retainOnlySweepsStaleChunks() {
		DocChunk keep = chunk("a.md", "keep");
		DocChunk drop = chunk("a.md", "drop");
		store.upsert(List.of(keep, drop), List.of(new float[] {1, 0}, new float[] {0, 1}));

		store.retainOnly(Set.of(keep.id()));

		assertThat(store.existingIds()).containsExactly(keep.id());
	}

	@Test
	void sourcesGroupsChunksBySourceFile() {
		store.upsert(List.of(chunk("a.md", "one"), chunk("a.md", "two"), chunk("b.md", "three")),
			List.of(new float[] {1, 0}, new float[] {0, 1}, new float[] {1, 1}));

		assertThat(store.sources()).containsExactly(
			new SourceSummary("a.md", 2), new SourceSummary("b.md", 1));
	}

	@Test
	void mismatchedSizesAreRejected() {
		assertThatThrownBy(() -> store.upsert(List.of(chunk("a.md", "x")), List.of()))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void cosineHandlesIdenticalOppositeAndZeroVectors() {
		assertThat(InMemoryVectorStore.cosine(new float[] {1, 2}, new float[] {1, 2}))
			.isCloseTo(1.0, within(1e-9));
		assertThat(InMemoryVectorStore.cosine(new float[] {1, 0}, new float[] {-1, 0}))
			.isCloseTo(-1.0, within(1e-9));
		assertThat(InMemoryVectorStore.cosine(new float[] {0, 0}, new float[] {1, 0})).isZero();
		assertThatThrownBy(() -> InMemoryVectorStore.cosine(new float[] {1}, new float[] {1, 2}))
			.isInstanceOf(IllegalArgumentException.class);
	}

}
