package com.aisandbox.audit.rag;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Loads the retrieval corpus from the classpath. The corpus is this repo's own knowledge:
 * the backend README and everything under {@code docs/} (ADRs, deployment guide), which the
 * Audit Gradle build copies into the jar under {@code rag-corpus/} (see
 * {@code Audit/build.gradle}), plus the assistant's existing {@code app-context.md}.
 *
 * <p>Bundling at build time — rather than reading the working tree at runtime — keeps the
 * container image self-contained: the same jar indexes the same corpus wherever it runs, and
 * "docs changed" is naturally handled because rebuilding the jar re-copies the docs and the
 * indexer's content hashing picks up the diff.
 */
@Component
public class CorpusLoader {

	private static final String CORPUS_PATTERN = "classpath*:rag-corpus/**/*.md";
	private static final String CORPUS_MARKER = "rag-corpus/";
	private static final String APP_CONTEXT = "assistant/app-context.md";

	/** One corpus file: repo-relative name (e.g. {@code docs/adr/0006-....md}) + raw markdown. */
	public record CorpusDocument(String source, String content) {
	}

	public List<CorpusDocument> load() {
		List<CorpusDocument> documents = new ArrayList<>();
		try {
			PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
			for (Resource resource : resolver.getResources(CORPUS_PATTERN)) {
				documents.add(new CorpusDocument(relativeName(resource), read(resource)));
			}
			Resource appContext = resolver.getResource("classpath:" + APP_CONTEXT);
			if (appContext.exists()) {
				documents.add(new CorpusDocument(APP_CONTEXT, read(appContext)));
			}
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to scan RAG corpus resources", e);
		}
		// Deterministic order → deterministic indexing logs and stable tests.
		documents.sort(Comparator.comparing(CorpusDocument::source));
		return documents;
	}

	private static String relativeName(Resource resource) throws IOException {
		String uri = resource.getURI().toString();
		int marker = uri.indexOf(CORPUS_MARKER);
		// The pattern below CORPUS_PATTERN guarantees the marker is present; guard anyway so a
		// classloader oddity degrades to a long-but-unique name instead of an exception.
		return marker >= 0 ? uri.substring(marker + CORPUS_MARKER.length()) : uri;
	}

	private static String read(Resource resource) throws IOException {
		return resource.getContentAsString(StandardCharsets.UTF_8);
	}

}
