package com.askapp.audit.trace;

import com.askapp.audit.rag.RagService;
import com.askapp.audit.rag.ScoredChunk;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiMetricsTest {

	private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
	private final AiMetrics metrics = new AiMetrics(registry);

	@Test
	void recordRetrievalTimesTheStageAndRecordsTheTopScore() {
		RagService.Retrieval retrieval = new RagService.Retrieval(
			List.of(new ScoredChunk("a.md", "H", "body", 0.8)),
			List.of(new ScoredChunk("a.md", "H", "body", 0.4)));

		metrics.recordRetrieval("chat", 42, retrieval);

		assertThat(registry.get("ai.retrieval.latency").tag("feature", "chat").timer().count()).isEqualTo(1);
		assertThat(registry.get("ai.retrieval.top.score").tag("feature", "chat").summary().totalAmount())
			.isEqualTo(0.8);
	}

	@Test
	void recordRetrievalWithNoResultsSkipsTheTopScore() {
		metrics.recordRetrieval("mcp_search", 5, new RagService.Retrieval(List.of(), List.of()));

		assertThat(registry.get("ai.retrieval.latency").tag("feature", "mcp_search").timer().count()).isEqualTo(1);
		assertThat(registry.find("ai.retrieval.top.score").tag("feature", "mcp_search").summary()).isNull();
	}

	@Test
	void recordChatTimesTheProviderCall() {
		metrics.recordChat(100);

		assertThat(registry.get("ai.assistant.chat.latency").timer().count()).isEqualTo(1);
	}

	@Test
	void recordBlockedChatIncrementsByCategory() {
		metrics.recordBlockedChat("jwt");

		assertThat(registry.get("ai.assistant.chat.blocked").tag("category", "jwt").counter().count())
			.isEqualTo(1.0);
	}

}
