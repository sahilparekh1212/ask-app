package com.askapp.audit.rag;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * One retrievable unit of the docs corpus: a heading-scoped slice of a markdown file.
 *
 * <p>The id is a SHA-256 over source + content, which is what makes startup re-indexing
 * incremental: an unchanged chunk hashes to an id the vector store already has and is
 * skipped without an embeddings API call; an edited chunk hashes to a new id, gets embedded,
 * and its stale predecessor is deleted by the retain-only sweep.
 */
public record DocChunk(String id, String source, String heading, String content) {

	public static DocChunk of(String source, String heading, String content) {
		return new DocChunk(sha256(source + "\n" + content), source, heading, content);
	}

	static String sha256(String text) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			// Every JVM ships SHA-256 (required by the Java security spec).
			throw new IllegalStateException("SHA-256 unavailable", e);
		}
	}

}
