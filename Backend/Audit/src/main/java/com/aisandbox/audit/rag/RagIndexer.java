package com.aisandbox.audit.rag;

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
 * happened, never fail startup.
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
	private final MarkdownChunker chunker;
	private final EmbeddingClient embeddingClient;
	private final VectorStore vectorStore;

	public RagIndexer(RagProperties properties, CorpusLoader corpusLoader, MarkdownChunker chunker,
			EmbeddingClient embeddingClient, VectorStore vectorStore) {
		this.properties = properties;
		this.corpusLoader = corpusLoader;
		this.chunker = chunker;
		this.embeddingClient = embeddingClient;
		this.vectorStore = vectorStore;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (!properties.isConfigured()) {
			log.info("RAG indexing skipped: not configured (set VOYAGE_API_KEY to enable)");
			return;
		}
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
			corpus.addAll(chunker.chunk(document.source(), document.content()));
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
