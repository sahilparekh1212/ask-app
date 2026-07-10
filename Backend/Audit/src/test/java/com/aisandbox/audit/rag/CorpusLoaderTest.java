package com.aisandbox.audit.rag;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs against the real classpath: the Gradle build copies the backend README + docs/ into
 * {@code rag-corpus/} (see Audit/build.gradle processResources), so this doubles as a build
 * check that the corpus actually made it into the resources output.
 */
class CorpusLoaderTest {

	private final CorpusLoader loader = new CorpusLoader();

	@Test
	void loadsTheBundledCorpusPlusAppContext() {
		List<CorpusLoader.CorpusDocument> documents = loader.load();

		assertThat(documents).isNotEmpty();
		assertThat(documents).anySatisfy(doc -> assertThat(doc.source()).isEqualTo("README.md"));
		assertThat(documents).anySatisfy(doc -> assertThat(doc.source()).startsWith("docs/adr/"));
		assertThat(documents).anySatisfy(doc -> assertThat(doc.source()).isEqualTo("assistant/app-context.md"));
		assertThat(documents).allSatisfy(doc -> assertThat(doc.content()).isNotBlank());
	}

	@Test
	void sourcesAreSortedAndUnique() {
		List<String> sources = loader.load().stream().map(CorpusLoader.CorpusDocument::source).toList();

		assertThat(sources).isSorted();
		assertThat(sources).doesNotHaveDuplicates();
	}

}
