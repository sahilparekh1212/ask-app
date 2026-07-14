package com.askapp.audit.rag;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a markdown document into retrieval chunks along its heading structure.
 *
 * <p>Heading-based splitting (rather than fixed-size windows) keeps each chunk a coherent
 * topic — an ADR's "Decision" section stays one unit instead of being cut mid-sentence —
 * which is what makes the retrieved text directly quotable by an LLM. Sections that exceed
 * {@code rag.max-chunk-chars} are further split on paragraph boundaries so a single giant
 * section (e.g. a long TODO entry) can't blow past the embedding model's context.
 */
@Component
public class MarkdownChunker {

	private final int maxChunkChars;

	public MarkdownChunker(RagProperties properties) {
		this.maxChunkChars = properties.getMaxChunkChars();
	}

	public List<DocChunk> chunk(String source, String markdown) {
		List<DocChunk> chunks = new ArrayList<>();
		String heading = "(intro)";
		StringBuilder section = new StringBuilder();
		for (String line : markdown.split("\n", -1)) {
			if (isHeading(line)) {
				flushSection(chunks, source, heading, section.toString());
				heading = line.replaceFirst("^#{1,6}\\s*", "").trim();
				section.setLength(0);
			}
			section.append(line).append('\n');
		}
		flushSection(chunks, source, heading, section.toString());
		return chunks;
	}

	private static boolean isHeading(String line) {
		// ATX headings only (#, ##, ...). Fenced code blocks in this corpus don't start lines
		// with '#', so a full markdown parser would buy nothing here.
		return line.matches("^#{1,6}\\s.*");
	}

	private void flushSection(List<DocChunk> chunks, String source, String heading, String text) {
		String trimmed = text.strip();
		if (trimmed.isEmpty()) {
			return;
		}
		if (trimmed.length() <= maxChunkChars) {
			chunks.add(DocChunk.of(source, heading, trimmed));
			return;
		}
		// Oversized section: greedily pack paragraphs up to the limit. A single paragraph
		// larger than the limit is emitted alone (embedding providers truncate, which is an
		// acceptable tail case for prose) rather than cut mid-sentence.
		StringBuilder piece = new StringBuilder();
		for (String paragraph : trimmed.split("\n\\s*\n")) {
			if (piece.length() > 0 && piece.length() + paragraph.length() + 2 > maxChunkChars) {
				chunks.add(DocChunk.of(source, heading, piece.toString().strip()));
				piece.setLength(0);
			}
			piece.append(paragraph).append("\n\n");
		}
		if (!piece.toString().strip().isEmpty()) {
			chunks.add(DocChunk.of(source, heading, piece.toString().strip()));
		}
	}

}
