package com.askapp.audit.rag.eval;

import java.util.List;

/**
 * Pure retrieval-metric math for the RAG eval (ADR-0013). Deliberately separate from the live
 * runner ({@code RagEvalTest}) so the arithmetic is unit-tested ({@code RagEvalMetricsTest}) without
 * an embeddings key — the runner only wires these functions to real retrieval results.
 *
 * <p>All metrics take the ranked list of retrieved source paths (best first, already truncated to
 * top-k by the caller) and a question's expected source labels, and return a value in [0, 1].
 */
public final class RagEvalMetrics {

	private RagEvalMetrics() {
	}

	/**
	 * A retrieved {@code source} matches an expected {@code label} when its path ends with the label
	 * (suffix match) — so a label like {@code audit/rag/RagService.java} matches the full bundled
	 * path regardless of the package prefix, and survives package/path renames. Paths are normalised
	 * to forward slashes first.
	 */
	public static boolean matches(String source, String label) {
		String s = source.replace('\\', '/');
		String e = label.replace('\\', '/');
		return s.equals(e) || s.endsWith("/" + e);
	}

	private static boolean matchesAny(String source, List<String> expected) {
		for (String label : expected) {
			if (matches(source, label)) {
				return true;
			}
		}
		return false;
	}

	/** 1 if any expected source appears anywhere in the ranked results, else 0. */
	public static double hit(List<String> ranked, List<String> expected) {
		for (String source : ranked) {
			if (matchesAny(source, expected)) {
				return 1.0;
			}
		}
		return 0.0;
	}

	/** Fraction of the expected sources that appear anywhere in the ranked results. */
	public static double recall(List<String> ranked, List<String> expected) {
		if (expected.isEmpty()) {
			return 0.0;
		}
		int found = 0;
		for (String label : expected) {
			for (String source : ranked) {
				if (matches(source, label)) {
					found++;
					break;
				}
			}
		}
		return (double) found / expected.size();
	}

	/** Reciprocal rank of the first result matching any expected source (0 if none match). */
	public static double reciprocalRank(List<String> ranked, List<String> expected) {
		for (int i = 0; i < ranked.size(); i++) {
			if (matchesAny(ranked.get(i), expected)) {
				return 1.0 / (i + 1);
			}
		}
		return 0.0;
	}

	/**
	 * nDCG over binary relevance: a result gains 1 if it matches an expected source, discounted by
	 * {@code 1/log2(rank+1)}. The ideal DCG places {@code min(|expected|, |ranked|)} relevant results
	 * at the top.
	 */
	public static double ndcg(List<String> ranked, List<String> expected) {
		double dcg = 0.0;
		for (int i = 0; i < ranked.size(); i++) {
			if (matchesAny(ranked.get(i), expected)) {
				dcg += 1.0 / log2(i + 2);
			}
		}
		int idealHits = Math.min(expected.size(), ranked.size());
		double idcg = 0.0;
		for (int i = 0; i < idealHits; i++) {
			idcg += 1.0 / log2(i + 2);
		}
		return idcg == 0.0 ? 0.0 : dcg / idcg;
	}

	private static double log2(int value) {
		return Math.log(value) / Math.log(2);
	}

}
