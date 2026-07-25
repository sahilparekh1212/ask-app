package com.askapp.audit.rag.rerank;

import com.askapp.audit.rag.ScoredChunk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityRerankerTest {

	private final IdentityReranker reranker = new IdentityReranker();

	private static ScoredChunk chunk(String id) {
		return new ScoredChunk(id + ".md", id, id + " body", 0.5);
	}

	@Test
	void candidatePoolSizeIsExactlyTopKSoNothingExtraIsFetched() {
		assertThat(reranker.candidatePoolSize(5)).isEqualTo(5);
		assertThat(reranker.candidatePoolSize(1)).isEqualTo(1);
	}

	@Test
	void rerankReturnsTheFirstTopKUntouched() {
		List<ScoredChunk> candidates = List.of(chunk("a"), chunk("b"), chunk("c"));
		assertThat(reranker.rerank("q", candidates, 2)).containsExactly(chunk("a"), chunk("b"));
	}

	@Test
	void rerankReturnsAllWhenFewerCandidatesThanTopK() {
		List<ScoredChunk> candidates = List.of(chunk("a"));
		assertThat(reranker.rerank("q", candidates, 5)).isEqualTo(candidates);
	}

	@Test
	void rerankOnEmptyCandidatesIsEmpty() {
		assertThat(reranker.rerank("q", List.of(), 5)).isEmpty();
	}

}
