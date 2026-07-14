package com.askapp.audit.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Startup indexing pipeline: load corpus → chunk → embed what's new → sweep what's stale.
 * The seeder pattern precedent from {@code DemoDataSeeder}: run once on boot, log what
 * happened, never fail startup — and never delay it either: the work runs on a background
 * thread so the audit API is ready immediately while the index warms up.
 *
 * <p>Incremental by content hash — a chunk's id is a SHA-256 of its source + content, so an
 * unchanged doc costs zero embeddings API calls on restart, an edited section is re-embedded
 * under a new id, and the retain-only sweep deletes ids no longer produced by the corpus.
 * That makes "what re-indexes when docs change" a non-event: rebuild the jar (docs are
 * bundled at build time), restart, and the diff is computed against the store itself.
 */
@Component
public class RagIndexer implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(RagIndexer.class);

	private final RagProperties properties;
	private final CorpusLoader corpusLoader;
	private final MarkdownChunker markdownChunker;
	private final CodeChunker codeChunker;
	private final EmbeddingClient embeddingClient;
	private final VectorStore vectorStore;

	public RagIndexer(RagProperties properties, CorpusLoader corpusLoader, MarkdownChunker markdownChunker,
			CodeChunker codeChunker, EmbeddingClient embeddingClient, VectorStore vectorStore) {
		this.properties = properties;
		this.corpusLoader = corpusLoader;
		this.markdownChunker = markdownChunker;
		this.codeChunker = codeChunker;
		this.embeddingClient = embeddingClient;
		this.vectorStore = vectorStore;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (!properties.isConfigured()) {
			log.info("RAG indexing skipped: not configured (set VOYAGE_API_KEY to enable)");
			return;
		}
		// Index on a background thread: with the repo-as-corpus this is hundreds of embedding
		// calls, and an ApplicationRunner blocking here would keep the whole audit API
		// unavailable for ~a minute on every deploy/restart. Retrieval already degrades
		// gracefully while the store fills (chat falls back to the un-retrieved prompt, MCP
		// search reports empty), so readiness doesn't need to wait for it.
		Thread indexerThread = new Thread(this::indexSafely, "rag-indexer");
		indexerThread.setDaemon(true);
		indexerThread.start();
	}

	void indexSafely() {
		try {
			index();
		} catch (RuntimeException e) {
			// An embeddings-provider outage must not take down the audit API; retrieval simply
			// stays at whatever the store already holds (possibly empty) until next restart.
			log.error("RAG indexing failed; retrieval will serve the existing index", e);
		}
	}

	void index() {
		List<CorpusLoader.CorpusDocument> documents = corpusLoader.load();
		List<DocChunk> corpus = new ArrayList<>();
		for (CorpusLoader.CorpusDocument document : documents) {
			// Markdown docs chunk by heading; source files by size on line boundaries.
			List<DocChunk> chunks = document.source().endsWith(".md")
				? markdownChunker.chunk(document.source(), document.content())
				: codeChunker.chunk(document.source(), document.content());
			corpus.addAll(chunks);
		}
		Set<String> liveIds = corpus.stream().map(DocChunk::id).collect(Collectors.toSet());
		Set<String> existing = vectorStore.existingIds();

		List<DocChunk> toEmbed = corpus.stream()
			.filter(chunk -> !existing.contains(chunk.id()))
			.toList();
		if (!toEmbed.isEmpty()) {
			List<float[]> vectors = embeddingClient.embedDocuments(
				toEmbed.stream().map(DocChunk::content).toList());
			vectorStore.upsert(toEmbed, vectors);
		}
		vectorStore.retainOnly(liveIds);

		log.info("RAG index ready: {} chunks from {} docs ({} newly embedded, {} reused, {} swept)",
			liveIds.size(), documents.size(), toEmbed.size(),
			corpus.size() - toEmbed.size(), Math.max(0, existing.size() - (corpus.size() - toEmbed.size())));
	}

}
