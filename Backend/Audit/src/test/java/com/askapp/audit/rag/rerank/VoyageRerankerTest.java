package com.askapp.audit.rag.rerank;

import com.askapp.audit.rag.RagProperties;
import com.askapp.audit.rag.ScoredChunk;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class VoyageRerankerTest {

	private static final String URL = "https://api.voyageai.com/v1/rerank";

	private final RagProperties ragProperties =
		new RagProperties(true, "test-key", "voyage-3.5-lite", 1024, 2000, 5);
	private final RerankProperties rerankProperties =
		new RerankProperties(true, "rerank-2.5-lite", 20);

	private final RestClient.Builder builder = RestClient.builder().baseUrl("https://api.voyageai.com/v1");
	private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
	private final VoyageReranker reranker = new VoyageReranker(ragProperties, rerankProperties, builder.build());

	private static ScoredChunk chunk(String id, double score) {
		return new ScoredChunk(id + ".md", id, id + " body", score);
	}

	@Test
	void candidatePoolSizeIsTheWiderOfTopKAndConfiguredPool() {
		assertThat(reranker.candidatePoolSize(5)).isEqualTo(20);   // configured pool wins
		assertThat(reranker.candidatePoolSize(25)).isEqualTo(25);  // but never below topK
	}

	@Test
	void reranksByRelevanceAndReplacesTheScore() {
		List<ScoredChunk> candidates = List.of(chunk("a", 0.9), chunk("b", 0.7), chunk("c", 0.5));
		// The reranker says candidate index 1 ("b") is most relevant, then index 0 ("a").
		server.expect(requestTo(URL))
			.andExpect(method(HttpMethod.POST))
			.andExpect(jsonPath("$.model").value("rerank-2.5-lite"))
			.andExpect(jsonPath("$.query").value("q"))
			.andExpect(jsonPath("$.top_k").value(2))
			.andExpect(jsonPath("$.documents[0]").value("a body"))
			.andRespond(withSuccess("""
				{"data":[{"index":1,"relevance_score":0.98},{"index":0,"relevance_score":0.91}]}
				""", MediaType.APPLICATION_JSON));

		List<ScoredChunk> result = reranker.rerank("q", candidates, 2);

		// Reordered by relevance, and each chunk now carries its relevance score, not its cosine one.
		assertThat(result).containsExactly(chunk("b", 0.98), chunk("a", 0.91));
		server.verify();
	}

	@Test
	void sortsDefensivelyByScoreRegardlessOfResponseOrder() {
		List<ScoredChunk> candidates = List.of(chunk("a", 0.9), chunk("b", 0.7));
		server.expect(requestTo(URL)).andRespond(withSuccess("""
			{"data":[{"index":0,"relevance_score":0.10},{"index":1,"relevance_score":0.90}]}
			""", MediaType.APPLICATION_JSON));

		assertThat(reranker.rerank("q", candidates, 2))
			.containsExactly(chunk("b", 0.90), chunk("a", 0.10));
	}

	@Test
	void dropsOutOfRangeIndicesFromTheResponse() {
		List<ScoredChunk> candidates = List.of(chunk("a", 0.9), chunk("b", 0.7));
		server.expect(requestTo(URL)).andRespond(withSuccess("""
			{"data":[{"index":5,"relevance_score":0.99},{"index":0,"relevance_score":0.80}]}
			""", MediaType.APPLICATION_JSON));

		assertThat(reranker.rerank("q", candidates, 2)).containsExactly(chunk("a", 0.80));
	}

	@Test
	void failSoftFallsBackToCosineOrderOnProviderError() {
		List<ScoredChunk> candidates = List.of(chunk("a", 0.9), chunk("b", 0.7), chunk("c", 0.5));
		server.expect(requestTo(URL)).andRespond(withServerError());

		// The first topK (2) in the given (cosine) order — reranking degraded, not fatal.
		assertThat(reranker.rerank("q", candidates, 2))
			.containsExactly(chunk("a", 0.9), chunk("b", 0.7));
	}

	@Test
	void failSoftOnEmptyResponseData() {
		List<ScoredChunk> candidates = List.of(chunk("a", 0.9), chunk("b", 0.7));
		server.expect(requestTo(URL)).andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));

		assertThat(reranker.rerank("q", candidates, 1)).containsExactly(chunk("a", 0.9));
	}

	@Test
	void emptyCandidatesShortCircuitWithoutCallingTheApi() {
		// The strict mock server has no expectation registered, so any HTTP call would fail here.
		assertThat(reranker.rerank("q", List.of(), 5)).isEmpty();
	}

	@Test
	void lazyClientBuildIsMemoizedAndPerformsNoIo() {
		VoyageReranker lazy = new VoyageReranker(ragProperties, rerankProperties);

		RestClient first = lazy.getClient();

		assertThat(first).isNotNull().isSameAs(lazy.getClient());
	}

}
