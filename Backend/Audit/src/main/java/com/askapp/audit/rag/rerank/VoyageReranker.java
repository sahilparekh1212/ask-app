package com.askapp.audit.rag.rerank;

import com.askapp.audit.rag.RagProperties;
import com.askapp.audit.rag.ScoredChunk;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import java.util.Comparator;
import java.util.List;

/**
 * {@link Reranker} backed by the Voyage AI rerank API — the second stage of two-stage retrieval
 * (ADR-0012). It reuses the embeddings account: the same {@code VOYAGE_API_KEY} from
 * {@link RagProperties}, the same lazy-client / no-I/O-on-construction posture as
 * {@code VoyageEmbeddingClient}, and the same "nothing from an inbound HTTP request reaches the
 * provider" guarantee (only the screened query and corpus chunk text are sent).
 *
 * <p><b>Fail-soft.</b> Reranking is an enhancement, not a dependency: any error (provider outage,
 * malformed response) is caught and the first-stage cosine order is returned instead, so a rerank
 * failure degrades <em>ordering</em> rather than blanking retrieval.
 */
public class VoyageReranker implements Reranker {

	private static final String BASE_URL = "https://api.voyageai.com/v1";
	private static final Logger log = LoggerFactory.getLogger(VoyageReranker.class);

	private final RagProperties ragProperties;
	private final RerankProperties rerankProperties;
	private volatile RestClient client;

	public VoyageReranker(RagProperties ragProperties, RerankProperties rerankProperties) {
		this.ragProperties = ragProperties;
		this.rerankProperties = rerankProperties;
	}

	/** Test constructor: inject a pre-built (mock) RestClient. */
	VoyageReranker(RagProperties ragProperties, RerankProperties rerankProperties, RestClient client) {
		this.ragProperties = ragProperties;
		this.rerankProperties = rerankProperties;
		this.client = client;
	}

	@Override
	public int candidatePoolSize(int topK) {
		// Recall a wider pool than we return so the reranker has something to reorder, but never
		// fewer than topK.
		return Math.max(topK, rerankProperties.getCandidatePoolSize());
	}

	@Override
	public List<ScoredChunk> rerank(String query, List<ScoredChunk> candidates, int topK) {
		if (candidates.isEmpty()) {
			return candidates;
		}
		int k = Math.min(topK, candidates.size());
		try {
			List<String> documents = candidates.stream().map(ScoredChunk::content).toList();
			RerankResponse response = getClient().post()
				.uri("/rerank")
				.body(new RerankRequest(rerankProperties.getModel(), query, documents, k))
				.retrieve()
				.body(RerankResponse.class);
			if (response == null || response.data() == null || response.data().isEmpty()) {
				throw new IllegalStateException("Voyage rerank returned no results");
			}
			return response.data().stream()
				// The API returns the reranked subset; sort defensively (no documented ordering
				// guarantee) and drop any out-of-range index rather than trusting the payload.
				.filter(result -> result.index() >= 0 && result.index() < candidates.size())
				.sorted(Comparator.comparingDouble(RerankResult::relevanceScore).reversed())
				.limit(k)
				.map(result -> withScore(candidates.get(result.index()), result.relevanceScore()))
				.toList();
		} catch (RuntimeException e) {
			// Fail-soft (ADR-0012): fall back to the first-stage cosine order so a rerank outage
			// never blanks retrieval — the same contract retrieval already has for the chat path.
			log.warn("Rerank failed, falling back to cosine order: {}", e.getClass().getSimpleName());
			return candidates.size() <= topK ? candidates : List.copyOf(candidates.subList(0, topK));
		}
	}

	private static ScoredChunk withScore(ScoredChunk chunk, double score) {
		return new ScoredChunk(chunk.source(), chunk.heading(), chunk.content(), score);
	}

	// Package-private lazy build, mirroring VoyageEmbeddingClient: constructing the client does no
	// I/O, and RagService never calls rerank when RAG is unconfigured (no key).
	RestClient getClient() {
		RestClient existing = client;
		if (existing != null) {
			return existing;
		}
		synchronized (this) {
			if (client == null) {
				client = RestClient.builder()
					.baseUrl(BASE_URL)
					.defaultHeader("Authorization", "Bearer " + ragProperties.getApiKey())
					.build();
			}
			return client;
		}
	}

	record RerankRequest(
		String model,
		String query,
		List<String> documents,
		@JsonProperty("top_k") int topK) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record RerankResponse(List<RerankResult> data) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record RerankResult(int index, @JsonProperty("relevance_score") double relevanceScore) {
	}

}
