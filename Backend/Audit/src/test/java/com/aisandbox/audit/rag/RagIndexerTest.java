package com.aisandbox.audit.rag;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RagIndexerTest {

	private static final RagProperties CONFIGURED =
		new RagProperties(true, "key", "voyage-3.5-lite", 2, 2000, 5);
	private static final RagProperties NO_KEY =
		new RagProperties(true, "", "voyage-3.5-lite", 2, 2000, 5);

	private final CorpusLoader corpusLoader = mock(CorpusLoader.class);
	private final MarkdownChunker chunker = new MarkdownChunker(CONFIGURED);
	private final EmbeddingClient embeddingClient = mock(EmbeddingClient.class);
	private final VectorStore vectorStore = mock(VectorStore.class);

	private RagIndexer indexer(RagProperties properties) {
		return new RagIndexer(properties, corpusLoader, chunker, embeddingClient, vectorStore);
	}

	@Test
	void skipsEntirelyWhenNotConfigured() {
		indexer(NO_KEY).run(null);

		verifyNoInteractions(corpusLoader, embeddingClient, vectorStore);
	}

	@Test
	void embedsAndStoresNewChunksThenSweeps() {
		when(corpusLoader.load()).thenReturn(List.of(
			new CorpusLoader.CorpusDocument("README.md", "# Title\nSome intro.")));
		when(vectorStore.existingIds()).thenReturn(Set.of());
		when(embeddingClient.embedDocuments(anyList())).thenReturn(List.of(new float[] {1, 0}));

		indexer(CONFIGURED).run(null);

		verify(embeddingClient).embedDocuments(List.of("# Title\nSome intro."));
		verify(vectorStore).upsert(anyList(), anyList());
		verify(vectorStore).retainOnly(anySet());
	}

	@Test
	void unchangedChunksCostNoEmbeddingCalls() {
		DocChunk existing = DocChunk.of("README.md", "(intro)", "# Title\nSome intro.");
		when(corpusLoader.load()).thenReturn(List.of(
			new CorpusLoader.CorpusDocument("README.md", "# Title\nSome intro.")));
		when(vectorStore.existingIds()).thenReturn(Set.of(existing.id()));

		indexer(CONFIGURED).run(null);

		verifyNoInteractions(embeddingClient);
		verify(vectorStore, never()).upsert(anyList(), anyList());
		verify(vectorStore).retainOnly(Set.of(existing.id()));
	}

	@Test
	void indexingFailureNeverFailsStartup() {
		when(corpusLoader.load()).thenThrow(new IllegalStateException("corpus unreadable"));

		indexer(CONFIGURED).run(null);
		// No exception propagated — the runner logs and startup continues.

		verify(vectorStore, never()).retainOnly(any());
	}

}
