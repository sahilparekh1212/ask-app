package com.askapp.audit.rag.rerank;

import com.askapp.audit.rag.RagProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RerankConfigurationTest {

	private static final RagProperties CONFIGURED =
		new RagProperties(true, "key", "voyage-3.5-lite", 1024, 2000, 5);
	private static final RagProperties NO_KEY =
		new RagProperties(true, "", "voyage-3.5-lite", 1024, 2000, 5);

	private final RerankConfiguration config = new RerankConfiguration();

	private static RerankProperties rerank(boolean enabled) {
		return new RerankProperties(enabled, "rerank-2.5-lite", 20);
	}

	@Test
	void wiresVoyageRerankerWhenEnabledAndRagConfigured() {
		assertThat(config.reranker(CONFIGURED, rerank(true))).isInstanceOf(VoyageReranker.class);
	}

	@Test
	void wiresIdentityRerankerWhenDisabled() {
		assertThat(config.reranker(CONFIGURED, rerank(false))).isInstanceOf(IdentityReranker.class);
	}

	@Test
	void wiresIdentityRerankerWhenRagIsUnconfigured() {
		// No key → the reranker has nothing to authenticate with, so it stays a no-op even though
		// it is "enabled" (search never runs unconfigured anyway).
		assertThat(config.reranker(NO_KEY, rerank(true))).isInstanceOf(IdentityReranker.class);
	}

}
