package com.askapp.audit.rag;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagServiceTest {

	private static final RagProperties CONFIGURED =
		new RagProperties(true, "key", "voyage-3.5-lite", 2, 2000, 5);
	private static final RagProperties NO_KEY =
		new RagProperties(true, "", "voyage-3.5-lite", 2, 2000, 5);
	private static final RagProperties DISABLED =
		new RagProperties(false, "key", "voyage-3.5-lite", 2, 2000, 5);

	private final EmbeddingClient embeddingClient = mock(EmbeddingClient.class);
	private final VectorStore vectorStore = mock(VectorStore.class);

	private RagService service(RagProperties properties) {
		return new RagService(properties, embeddingClient, vectorStore);
	}

	@Test
	void configuredRequiresKeyAndEnabledFlag() {
		assertThat(service(CONFIGURED).isConfigured()).isTrue();
		assertThat(service(NO_KEY).isConfigured()).isFalse();
		assertThat(service(DISABLED).isConfigured()).isFalse();
	}

	@Test
	void readyRequiresANonEmptyIndex() {
		when(vectorStore.count()).thenReturn(0L);
		assertThat(service(CONFIGURED).isReady()).isFalse();

		when(vectorStore.count()).thenReturn(3L);
		assertThat(service(CONFIGURED).isReady()).isTrue();
	}

	@Test
	void searchEmbedsTheQueryAndDelegatesToTheStore() {
		float[] queryVector = {1, 0};
		List<ScoredChunk> expected = List.of(new ScoredChunk("a.md", "H", "text", 0.8));
		when(embeddingClient.embedQuery("how does auth work?")).thenReturn(queryVector);
		when(vectorStore.search(queryVector, 5)).thenReturn(expected);

		assertThat(service(CONFIGURED).search("how does auth work?", null)).isEqualTo(expected);
	}

	@Test
	void topKIsClampedToSaneBounds() {
		when(embeddingClient.embedQuery(any())).thenReturn(new float[] {1, 0});

		service(CONFIGURED).search("q", 999);
		verify(vectorStore).search(any(), eq(RagService.MAX_TOP_K));

		service(CONFIGURED).search("q", 0);
		verify(vectorStore).search(any(), eq(1));
	}

	@Test
	void searchWhenUnconfiguredIsAnError() {
		assertThatThrownBy(() -> service(NO_KEY).search("q", null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("VOYAGE_API_KEY");
	}

	@Test
	void sourcesAndCountDelegateToTheStore() {
		when(vectorStore.sources()).thenReturn(List.of(new SourceSummary("a.md", 2)));
		when(vectorStore.count()).thenReturn(2L);

		assertThat(service(CONFIGURED).sources()).containsExactly(new SourceSummary("a.md", 2));
		assertThat(service(CONFIGURED).chunkCount()).isEqualTo(2);
	}

}
