package com.askapp.audit.rag;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class VoyageEmbeddingClientTest {

	private static final String URL = "https://api.voyageai.com/v1/embeddings";

	private final RagProperties properties =
		new RagProperties(true, "test-key", "voyage-3.5-lite", 2, 2000, 5);

	private final RestClient.Builder builder = RestClient.builder().baseUrl("https://api.voyageai.com/v1");
	private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
	private final VoyageEmbeddingClient client = new VoyageEmbeddingClient(properties, builder.build());

	@Test
	void embedsDocumentsWithDocumentInputType() {
		server.expect(requestTo(URL))
			.andExpect(method(HttpMethod.POST))
			.andExpect(jsonPath("$.model").value("voyage-3.5-lite"))
			.andExpect(jsonPath("$.input_type").value("document"))
			.andExpect(jsonPath("$.output_dimension").value(2))
			.andExpect(jsonPath("$.input[0]").value("first"))
			.andRespond(withSuccess("""
				{"data":[{"index":0,"embedding":[0.1,0.2]},{"index":1,"embedding":[0.3,0.4]}]}
				""", MediaType.APPLICATION_JSON));

		List<float[]> vectors = client.embedDocuments(List.of("first", "second"));

		assertThat(vectors).hasSize(2);
		assertThat(vectors.get(0)).containsExactly(0.1f, 0.2f);
		assertThat(vectors.get(1)).containsExactly(0.3f, 0.4f);
		server.verify();
	}

	@Test
	void embedsQueryWithQueryInputType() {
		server.expect(requestTo(URL))
			.andExpect(jsonPath("$.input_type").value("query"))
			.andRespond(withSuccess("""
				{"data":[{"index":0,"embedding":[0.5,0.6]}]}
				""", MediaType.APPLICATION_JSON));

		assertThat(client.embedQuery("question")).containsExactly(0.5f, 0.6f);
		server.verify();
	}

	@Test
	void reordersResponseItemsByIndex() {
		// The API documents no ordering guarantee — items must land by their input index.
		server.expect(requestTo(URL)).andRespond(withSuccess("""
			{"data":[{"index":1,"embedding":[9.0,9.0]},{"index":0,"embedding":[1.0,1.0]}]}
			""", MediaType.APPLICATION_JSON));

		List<float[]> vectors = client.embedDocuments(List.of("a", "b"));

		assertThat(vectors.get(0)).containsExactly(1.0f, 1.0f);
		assertThat(vectors.get(1)).containsExactly(9.0f, 9.0f);
	}

	@Test
	void splitsLargeCorporaIntoBatches() {
		List<String> texts = java.util.stream.IntStream.range(0, VoyageEmbeddingClient.MAX_BATCH + 1)
			.mapToObj(i -> "text " + i)
			.toList();
		String itemsFull = buildItems(VoyageEmbeddingClient.MAX_BATCH);
		server.expect(ExpectedCount.once(), requestTo(URL))
			.andRespond(withSuccess("{\"data\":[" + itemsFull + "]}", MediaType.APPLICATION_JSON));
		server.expect(ExpectedCount.once(), requestTo(URL))
			.andRespond(withSuccess("{\"data\":[" + buildItems(1) + "]}", MediaType.APPLICATION_JSON));

		assertThat(client.embedDocuments(texts)).hasSize(VoyageEmbeddingClient.MAX_BATCH + 1);
		server.verify();
	}

	@Test
	void mismatchedResponseSizeIsAnError() {
		server.expect(requestTo(URL)).andRespond(withSuccess("""
			{"data":[{"index":0,"embedding":[0.1,0.2]}]}
			""", MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> client.embedDocuments(List.of("a", "b")))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("did not match request size");
	}

	@Test
	void lazyClientBuildIsMemoizedAndPerformsNoIo() {
		VoyageEmbeddingClient lazy = new VoyageEmbeddingClient(properties);

		RestClient first = lazy.getClient();

		assertThat(first).isNotNull().isSameAs(lazy.getClient());
	}

	private static String buildItems(int count) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < count; i++) {
			if (i > 0) {
				sb.append(',');
			}
			sb.append("{\"index\":").append(i).append(",\"embedding\":[0.1,0.2]}");
		}
		return sb.toString();
	}

}
