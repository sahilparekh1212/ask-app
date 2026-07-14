package com.askapp.audit.rag;

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
		// The file-by-file code map + the component-level UI guide are part of the corpus so the
		// assistant can answer "which files do X" and "which component/endpoint" questions.
		assertThat(documents).anySatisfy(doc -> assertThat(doc.source()).isEqualTo("docs/code-map.md"));
		assertThat(documents).anySatisfy(doc -> assertThat(doc.source()).isEqualTo("docs/ui-guide.md"));
		// The backend SOURCE is bundled too (Audit/build.gradle processResources), so the assistant
		// can read the actual deployed code — a .java file, and a Gradle build file, both present.
		assertThat(documents).anySatisfy(doc -> assertThat(doc.source())
			.endsWith("com/askapp/audit/event/AuditEventPublisher.java"));
		assertThat(documents).anySatisfy(doc -> assertThat(doc.source()).isEqualTo("settings.gradle"));
		// The observability configs and the Angular UI source are bundled as well.
		assertThat(documents).anySatisfy(doc -> assertThat(doc.source()).startsWith("monitoring/"));
		assertThat(documents).anySatisfy(doc -> assertThat(doc.source())
			.isEqualTo("UI/src/app/features/assistant/assistant.component.ts"));
		assertThat(documents).allSatisfy(doc -> assertThat(doc.content()).isNotBlank());
	}

	@Test
	void sourcesAreSortedAndUnique() {
		List<String> sources = loader.load().stream().map(CorpusLoader.CorpusDocument::source).toList();

		assertThat(sources).isSorted();
		assertThat(sources).doesNotHaveDuplicates();
	}

}
