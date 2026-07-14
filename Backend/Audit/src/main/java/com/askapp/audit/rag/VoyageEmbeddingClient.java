package com.askapp.audit.rag;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * {@link EmbeddingClient} backed by the Voyage AI embeddings API. Anthropic has no
 * first-party embeddings endpoint; Voyage is the provider Anthropic's docs recommend, so
 * this is the RAG analog of {@code AnthropicLlmClient} — same server-side-key posture, same
 * lazy client construction so the app starts key-less, and nothing from any inbound HTTP
 * request can reach the provider (only corpus text and screened queries are embedded).
 */
@Component
public class VoyageEmbeddingClient implements EmbeddingClient {

	private static final String BASE_URL = "https://api.voyageai.com/v1";
	// The API caps a batch at 1,000 inputs; stay far below it so one request also stays
	// comfortably inside the per-request token ceiling for lite models.
	static final int MAX_BATCH = 128;

	private final RagProperties properties;
	private volatile RestClient client;

	@Autowired
	public VoyageEmbeddingClient(RagProperties properties) {
		this.properties = properties;
	}

	/** Test constructor: inject a pre-built (mock) RestClient. */
	VoyageEmbeddingClient(RagProperties properties, RestClient client) {
		this.properties = properties;
		this.client = client;
	}

	@Override
	public List<float[]> embedDocuments(List<String> texts) {
		List<float[]> vectors = new ArrayList<>(texts.size());
		for (int from = 0; from < texts.size(); from += MAX_BATCH) {
			List<String> batch = texts.subList(from, Math.min(from + MAX_BATCH, texts.size()));
			vectors.addAll(embed(batch, "document"));
		}
		return vectors;
	}

	@Override
	public float[] embedQuery(String text) {
		return embed(List.of(text), "query").get(0);
	}

	private List<float[]> embed(List<String> texts, String inputType) {
		EmbeddingsResponse response = getClient().post()
			.uri("/embeddings")
			.body(new EmbeddingsRequest(properties.getEmbeddingModel(), texts, inputType,
				properties.getEmbeddingDimension()))
			.retrieve()
			.body(EmbeddingsResponse.class);
		if (response == null || response.data() == null || response.data().size() != texts.size()) {
			throw new IllegalStateException("Voyage embeddings response did not match request size");
		}
		return response.data().stream()
			// The API documents no ordering guarantee; each item carries its input index.
			.sorted(Comparator.comparingInt(EmbeddingItem::index))
			.map(item -> toFloats(item.embedding()))
			.toList();
	}

	private static float[] toFloats(List<Double> values) {
		float[] vector = new float[values.size()];
		for (int i = 0; i < values.size(); i++) {
			vector[i] = values.get(i).floatValue();
		}
		return vector;
	}

	// Package-private lazy build, mirroring AnthropicLlmClient: constructing the client does
	// no I/O, and RagIndexer/RagService never call this when unconfigured.
	RestClient getClient() {
		RestClient existing = client;
		if (existing != null) {
			return existing;
		}
		synchronized (this) {
			if (client == null) {
				client = RestClient.builder()
					.baseUrl(BASE_URL)
					.defaultHeader("Authorization", "Bearer " + properties.getApiKey())
					.build();
			}
			return client;
		}
	}

	record EmbeddingsRequest(
		String model,
		List<String> input,
		@JsonProperty("input_type") String inputType,
		@JsonProperty("output_dimension") int outputDimension) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record EmbeddingsResponse(List<EmbeddingItem> data) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record EmbeddingItem(int index, List<Double> embedding) {
	}

}
