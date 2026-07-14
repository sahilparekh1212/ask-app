package com.askapp.audit.rag;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class CodeChunkerTest {

	private CodeChunker chunker(int maxChunkChars) {
		return new CodeChunker(new RagProperties(true, "key", "voyage-3.5-lite", 2, maxChunkChars, 5));
	}

	@Test
	void smallFileIsOneChunkLabelledWithItsLineRange() {
		List<DocChunk> chunks = chunker(2000).chunk("Foo.java", "class Foo {\n  int x;\n}");

		assertThat(chunks).hasSize(1);
		assertThat(chunks.get(0).source()).isEqualTo("Foo.java");
		assertThat(chunks.get(0).heading()).isEqualTo("lines 1-3");
		assertThat(chunks.get(0).content()).contains("class Foo").contains("int x");
	}

	@Test
	void largeFileSplitsOnLineBoundariesIntoLineRangedChunks() {
		// 12 short lines with a tiny limit forces several chunks.
		String code = IntStream.rangeClosed(1, 12).mapToObj(i -> "line-" + i)
			.collect(Collectors.joining("\n"));

		List<DocChunk> chunks = chunker(24).chunk("Big.java", code);

		assertThat(chunks.size()).isGreaterThan(1);
		assertThat(chunks.get(0).heading()).startsWith("lines 1-");
		// No line is lost or duplicated, and none is cut mid-line.
		String joined = chunks.stream().map(DocChunk::content).collect(Collectors.joining("\n"));
		for (int i = 1; i <= 12; i++) {
			assertThat(joined).contains("line-" + i);
		}
		assertThat(chunks).allSatisfy(c -> assertThat(c.content().length()).isLessThanOrEqualTo(24 + 8));
	}

	@Test
	void blankInputYieldsNoChunks() {
		assertThat(chunker(2000).chunk("Empty.java", "   \n\n  \n")).isEmpty();
	}

}
