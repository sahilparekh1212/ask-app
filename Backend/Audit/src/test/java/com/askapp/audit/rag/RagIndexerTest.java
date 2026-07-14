package com.askapp.audit.rag;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RagIndexerTest {

	private static final RagProperties CONFIGURED =
		new RagProperties(true, "key", "voyage-3.5-lite", 2, 2000, 5);
	private static final RagProperties NO_KEY =
		new RagProperties(true, "", "voyage-3.5-lite", 2, 2000, 5);

	private final CorpusLoader corpusLoader = mock(CorpusLoader.class);
	private final MarkdownChunker markdownChunker = new MarkdownChunker(CONFIGURED);
	private final CodeChunker codeChunker = new CodeChunker(CONFIGURED);
	private final EmbeddingClient embeddingClient = mock(EmbeddingClient.class);
	private final VectorStore vectorStore = mock(VectorStore.class);

	private RagIndexer indexer(RagProperties properties) {
		return new RagIndexer(properties, corpusLoader, markdownChunker, codeChunker, embeddingClient,
			vectorStore);
	}

	@Test
	void skipsEntirelyWhenNotConfigured() {
		indexer(NO_KEY).run(null);

		verifyNoInteractions(corpusLoader, embeddingClient, vectorStore);
	}

	@Test
	void runIndexesOnABackgroundThreadWithoutBlockingStartup() {
		// run() must return immediately and hand the work to a background thread — the whole
		// point of the async split is that Audit's readiness never waits on the embeddings
		// provider. The timeout verify observes the thread completing the full pipeline.
		when(corpusLoader.load()).thenReturn(List.of(
			new CorpusLoader.CorpusDocument("README.md", "# Title\nSome intro.")));
		when(vectorStore.existingIds()).thenReturn(Set.of());
		when(embeddingClient.embedDocuments(anyList())).thenReturn(List.of(new float[] {1, 0}));

		indexer(CONFIGURED).run(null);

		verify(vectorStore, timeout(5000)).retainOnly(anySet());
	}

	@Test
	void embedsAndStoresNewChunksThenSweeps() {
		when(corpusLoader.load()).thenReturn(List.of(
			new CorpusLoader.CorpusDocument("README.md", "# Title\nSome intro.")));
		when(vectorStore.existingIds()).thenReturn(Set.of());
		when(embeddingClient.embedDocuments(anyList())).thenReturn(List.of(new float[] {1, 0}));

		indexer(CONFIGURED).index();

		verify(embeddingClient).embedDocuments(List.of("# Title\nSome intro."));
		verify(vectorStore).upsert(anyList(), anyList());
		verify(vectorStore).retainOnly(anySet());
	}

	@Test
	void nonMarkdownDocumentsAreChunkedAsCode() {
		// A .java source file has no markdown headings; it must still be embedded (via CodeChunker),
		// with the chunk text being the code itself.
		when(corpusLoader.load()).thenReturn(List.of(new CorpusLoader.CorpusDocument(
			"Audit/src/main/java/com/askapp/audit/Foo.java", "class Foo {\n  int x;\n}")));
		when(vectorStore.existingIds()).thenReturn(Set.of());
		when(embeddingClient.embedDocuments(anyList())).thenReturn(List.of(new float[] {1, 0}));

		indexer(CONFIGURED).index();

		verify(embeddingClient).embedDocuments(List.of("class Foo {\n  int x;\n}"));
		verify(vectorStore).upsert(anyList(), anyList());
	}

	@Test
	void unchangedChunksCostNoEmbeddingCalls() {
		DocChunk existing = DocChunk.of("README.md", "(intro)", "# Title\nSome intro.");
		when(corpusLoader.load()).thenReturn(List.of(
			new CorpusLoader.CorpusDocument("README.md", "# Title\nSome intro.")));
		when(vectorStore.existingIds()).thenReturn(Set.of(existing.id()));

		indexer(CONFIGURED).index();

		verifyNoInteractions(embeddingClient);
		verify(vectorStore, never()).upsert(anyList(), anyList());
		verify(vectorStore).retainOnly(Set.of(existing.id()));
	}

	@Test
	void indexingFailureNeverPropagates() {
		when(corpusLoader.load()).thenThrow(new IllegalStateException("corpus unreadable"));

		indexer(CONFIGURED).indexSafely();
		// No exception propagated — the failure is logged and the app keeps serving.

		verify(vectorStore, never()).retainOnly(any());
	}

}
