package com.askapp.audit.rag;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Loads the retrieval corpus from the classpath. The corpus is this repo's own knowledge:
 * the backend README and everything under {@code docs/} (ADRs, deployment guide), the
 * assistant's {@code app-context.md}, <em>and the backend source itself</em> (Auth + Audit +
 * common Java, resources, and the Gradle build files) — all of which the Audit Gradle build
 * copies into the jar under {@code rag-corpus/} (see {@code Audit/build.gradle}). Markdown docs
 * are chunked by heading, source files by size (see {@link MarkdownChunker} / {@link CodeChunker}).
 *
 * <p>Bundling at build time — rather than reading the working tree at runtime — keeps the
 * container image self-contained: the same jar indexes the same corpus wherever it runs, and
 * because the image is built from {@code main} (merge → CD → deploy) the indexed source always
 * matches the deployed code. "Docs/code changed" is naturally handled: rebuilding the jar
 * re-copies the inputs and the indexer's content hashing picks up the diff.
 */
@Component
public class CorpusLoader {

	private static final String CORPUS_PATTERN = "classpath*:rag-corpus/**/*.md";
	// Source files bundled alongside the docs. Everything with a dot is scanned, then filtered to
	// the text/code extensions below (so a stray binary or the markdown docs aren't double-loaded).
	private static final String CODE_PATTERN = "classpath*:rag-corpus/**/*.*";
	private static final Set<String> CODE_EXTENSIONS = Set.of(
		// backend
		"java", "gradle", "properties", "xml",
		// infra / observability / deployment configs
		"yaml", "yml", "json", "conf",
		// frontend (Angular UI)
		"ts", "html", "scss");
	private static final String CORPUS_MARKER = "rag-corpus/";
	private static final String APP_CONTEXT = "assistant/app-context.md";

	/** One corpus file: repo-relative name (e.g. {@code docs/adr/0006-....md}) + raw content. */
	public record CorpusDocument(String source, String content) {
	}

	public List<CorpusDocument> load() {
		List<CorpusDocument> documents = new ArrayList<>();
		try {
			PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
			for (Resource resource : resolver.getResources(CORPUS_PATTERN)) {
				documents.add(new CorpusDocument(relativeName(resource), read(resource)));
			}
			for (Resource resource : resolver.getResources(CODE_PATTERN)) {
				String name = relativeName(resource);
				if (hasCodeExtension(name)) {
					String content = read(resource);
					// Skip empty files (e.g. an empty Angular *.scss) — nothing to embed, and a
					// blank chunk would only dilute retrieval.
					if (!content.isBlank()) {
						documents.add(new CorpusDocument(name, content));
					}
				}
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

	private static boolean hasCodeExtension(String name) {
		int dot = name.lastIndexOf('.');
		return dot >= 0 && CODE_EXTENSIONS.contains(name.substring(dot + 1).toLowerCase());
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
