package com.askapp.audit.rag;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a source-code file into retrieval chunks by size, on line boundaries. Unlike prose,
 * code has no markdown heading structure for {@link MarkdownChunker} to follow, so chunks are
 * packed up to {@code rag.max-chunk-chars} and labelled with their line range (e.g.
 * {@code "lines 1-48"}) — enough for the assistant to cite <em>where</em> in a file a snippet
 * came from. A single line longer than the limit is emitted on its own (embedding providers
 * truncate, an acceptable tail case) rather than cut mid-line.
 *
 * <p>Line boundaries (rather than character windows) keep chunks readable — a method body isn't
 * sliced through the middle of a token — which is what makes the retrieved code directly
 * quotable back to the user.
 */
@Component
public class CodeChunker {

	private final int maxChunkChars;

	public CodeChunker(RagProperties properties) {
		this.maxChunkChars = properties.getMaxChunkChars();
	}

	public List<DocChunk> chunk(String source, String code) {
		List<DocChunk> chunks = new ArrayList<>();
		String[] lines = code.split("\n", -1);
		StringBuilder buf = new StringBuilder();
		int startLine = 1;
		int lineNo = 0;
		for (String line : lines) {
			lineNo++;
			// Flush before appending when this line would push the chunk past the limit, so the
			// boundary falls between lines. Keep at least one line per chunk.
			if (buf.length() > 0 && buf.length() + line.length() + 1 > maxChunkChars) {
				flush(chunks, source, startLine, lineNo - 1, buf);
				startLine = lineNo;
			}
			buf.append(line).append('\n');
		}
		flush(chunks, source, startLine, lineNo, buf);
		return chunks;
	}

	private static void flush(List<DocChunk> chunks, String source, int startLine, int endLine, StringBuilder buf) {
		String text = buf.toString().strip();
		buf.setLength(0);
		if (!text.isEmpty()) {
			chunks.add(DocChunk.of(source, "lines " + startLine + "-" + endLine, text));
		}
	}

}
