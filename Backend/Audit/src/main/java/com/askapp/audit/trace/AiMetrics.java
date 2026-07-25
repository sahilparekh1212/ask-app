package com.askapp.audit.trace;

import com.askapp.audit.rag.RagService;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Micrometer meters for the AI features (ADR-0014), exported to Prometheus/Grafana alongside the
 * per-interaction {@code ai_trace} rows. Time-series for dashboards and alerting; the table holds the
 * per-interaction detail. Tagged by {@code feature} ({@code chat} / {@code mcp_search}).
 */
@Component
public class AiMetrics {

	private final MeterRegistry registry;

	public AiMetrics(MeterRegistry registry) {
		this.registry = registry;
	}

	/** Retrieval latency (embed → rerank) and the top result's score, per feature. */
	public void recordRetrieval(String feature, long latencyMs, RagService.Retrieval retrieval) {
		registry.timer("ai.retrieval.latency", "feature", feature).record(latencyMs, TimeUnit.MILLISECONDS);
		if (!retrieval.results().isEmpty()) {
			registry.summary("ai.retrieval.top.score", "feature", feature)
				.record(retrieval.results().get(0).score());
		}
	}

	/** End-to-end assistant chat latency (the provider call). */
	public void recordChat(long latencyMs) {
		registry.timer("ai.assistant.chat.latency").record(latencyMs, TimeUnit.MILLISECONDS);
	}

	/** A chat turn refused by the prompt screener, tagged with the screener category. */
	public void recordBlockedChat(String category) {
		registry.counter("ai.assistant.chat.blocked", "category", category).increment();
	}

}
