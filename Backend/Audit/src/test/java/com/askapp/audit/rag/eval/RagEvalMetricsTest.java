package com.askapp.audit.rag.eval;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit tests for the eval metric math. Not tagged {@code eval}, so it runs in the normal keyless
 * test task — the arithmetic the quality gate depends on is verified without an embeddings key.
 */
class RagEvalMetricsTest {

	private static final String RAG_SERVICE = "Audit/src/main/java/com/askapp/audit/rag/RagService.java";

	@Test
	void suffixMatchIgnoresThePackagePrefix() {
		assertThat(RagEvalMetrics.matches(RAG_SERVICE, "audit/rag/RagService.java")).isTrue();
		assertThat(RagEvalMetrics.matches("docs/adr/0001-x.md", "docs/adr/0001-x.md")).isTrue();
	}

	@Test
	void suffixMatchDoesNotCrossADirectoryBoundary() {
		// "rag/RagService.java" must not match ".../foorag/RagService.java" style tails.
		assertThat(RagEvalMetrics.matches("a/b/otherRagService.java", "RagService.java")).isFalse();
		assertThat(RagEvalMetrics.matches("auth/config/SecurityConfig.java", "audit/config/SecurityConfig.java"))
			.isFalse();
	}

	@Test
	void hitIsOneWhenAnyExpectedSourceIsPresent() {
		assertThat(RagEvalMetrics.hit(List.of("a.md", "b.md"), List.of("b.md"))).isEqualTo(1.0);
		assertThat(RagEvalMetrics.hit(List.of("a.md", "b.md"), List.of("z.md"))).isEqualTo(0.0);
	}

	@Test
	void recallIsTheFractionOfExpectedSourcesFound() {
		assertThat(RagEvalMetrics.recall(List.of("a.md", "b.md"), List.of("a.md", "z.md"))).isEqualTo(0.5);
		assertThat(RagEvalMetrics.recall(List.of("a.md", "b.md"), List.of("a.md", "b.md"))).isEqualTo(1.0);
		assertThat(RagEvalMetrics.recall(List.of("a.md"), List.of())).isEqualTo(0.0);
	}

	@Test
	void reciprocalRankUsesTheFirstMatchingPosition() {
		assertThat(RagEvalMetrics.reciprocalRank(List.of("x", "a", "b"), List.of("a"))).isEqualTo(0.5);
		assertThat(RagEvalMetrics.reciprocalRank(List.of("x", "y", "b"), List.of("b"))).isCloseTo(1.0 / 3, within(1e-9));
		assertThat(RagEvalMetrics.reciprocalRank(List.of("x", "y"), List.of("a"))).isEqualTo(0.0);
	}

	@Test
	void ndcgIsOneWhenTheOnlyExpectedSourceIsRankedFirst() {
		assertThat(RagEvalMetrics.ndcg(List.of("a", "x"), List.of("a"))).isEqualTo(1.0);
	}

	@Test
	void ndcgDiscountsALaterMatch() {
		// One expected source at rank 2: DCG = 1/log2(3), IDCG = 1/log2(2) = 1.
		double expected = (1.0 / (Math.log(3) / Math.log(2)));
		assertThat(RagEvalMetrics.ndcg(List.of("x", "a"), List.of("a"))).isCloseTo(expected, within(1e-9));
	}

	@Test
	void ndcgIsOneWhenBothExpectedSourcesTakeTheTopTwoRanks() {
		assertThat(RagEvalMetrics.ndcg(List.of("a", "b"), List.of("a", "b"))).isCloseTo(1.0, within(1e-9));
	}

	@Test
	void ndcgIsZeroWithNoMatches() {
		assertThat(RagEvalMetrics.ndcg(List.of("x", "y"), List.of("a"))).isEqualTo(0.0);
	}

}
