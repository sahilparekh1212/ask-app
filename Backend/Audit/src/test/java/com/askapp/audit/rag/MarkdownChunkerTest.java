package com.askapp.audit.rag;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownChunkerTest {

	private static RagProperties propertiesWithMaxChars(int maxChars) {
		return new RagProperties(true, "key", "voyage-3.5-lite", 1024, maxChars, 5);
	}

	private final MarkdownChunker chunker = new MarkdownChunker(propertiesWithMaxChars(2000));

	@Test
	void splitsOnHeadings() {
		String md = """
			# Title
			Intro text.

			## Section A
			Content A.

			## Section B
			Content B.
			""";

		List<DocChunk> chunks = chunker.chunk("README.md", md);

		assertThat(chunks).hasSize(3);
		assertThat(chunks.get(0).heading()).isEqualTo("Title");
		assertThat(chunks.get(0).content()).contains("# Title").contains("Intro text.");
		assertThat(chunks.get(1).heading()).isEqualTo("Section A");
		assertThat(chunks.get(1).content()).contains("Content A.");
		assertThat(chunks.get(2).heading()).isEqualTo("Section B");
		assertThat(chunks.get(2).content()).contains("Content B.");
	}

	@Test
	void eachSectionIsLabeledByItsOwnHeading() {
		// The heading label updates before the heading line is appended, so a section is
		// always labeled by the heading that opens it (not the one before it).
		List<DocChunk> chunks = chunker.chunk("a.md", "## Only Section\nBody text.\n");

		assertThat(chunks).hasSize(1);
		assertThat(chunks.get(0).heading()).isEqualTo("Only Section");
		assertThat(chunks.get(0).content()).contains("## Only Section").contains("Body text.");
	}

	@Test
	void oversizedSectionsSplitOnParagraphs() {
		MarkdownChunker tiny = new MarkdownChunker(propertiesWithMaxChars(40));
		String md = "# H\n" + "para one is right here.\n\npara two is right here.\n\npara three is here.";

		List<DocChunk> chunks = tiny.chunk("a.md", md);

		assertThat(chunks).hasSizeGreaterThan(1);
		assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.source()).isEqualTo("a.md"));
		String joined = String.join("\n", chunks.stream().map(DocChunk::content).toList());
		assertThat(joined).contains("para one").contains("para two").contains("para three");
	}

	@Test
	void aSingleParagraphLargerThanTheLimitIsEmittedWhole() {
		MarkdownChunker tiny = new MarkdownChunker(propertiesWithMaxChars(10));
		List<DocChunk> chunks = tiny.chunk("a.md", "one long unbroken paragraph without blank lines");

		assertThat(chunks).hasSize(1);
		assertThat(chunks.get(0).content()).contains("unbroken paragraph");
	}

	@Test
	void blankDocumentYieldsNoChunks() {
		assertThat(chunker.chunk("a.md", "\n\n   \n")).isEmpty();
	}

	@Test
	void chunkIdsAreStableContentHashes() {
		List<DocChunk> first = chunker.chunk("a.md", "# H\nSame content.");
		List<DocChunk> second = chunker.chunk("a.md", "# H\nSame content.");
		List<DocChunk> other = chunker.chunk("b.md", "# H\nSame content.");

		assertThat(first.get(0).id()).isEqualTo(second.get(0).id());
		// Same content in a different file is a different chunk (id covers the source).
		assertThat(first.get(0).id()).isNotEqualTo(other.get(0).id());
		assertThat(first.get(0).id()).hasSize(64).matches("[0-9a-f]+");
	}

}
