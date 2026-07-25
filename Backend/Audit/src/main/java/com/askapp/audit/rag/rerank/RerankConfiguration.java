package com.askapp.audit.rag.rerank;

import com.askapp.audit.rag.RagProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Selects the {@link Reranker} implementation once at startup (ADR-0012). The real
 * {@link VoyageReranker} is wired only when reranking is enabled <em>and</em> RAG is configured
 * (an embeddings key is present, which the reranker reuses); otherwise the {@link IdentityReranker}
 * passthrough is wired, so a missing key or {@code rag.rerank.enabled=false} restores exact
 * single-stage retrieval without any reranker network calls.
 */
@Configuration
public class RerankConfiguration {

	@Bean
	public Reranker reranker(RagProperties ragProperties, RerankProperties rerankProperties) {
		if (rerankProperties.isEnabled() && ragProperties.isConfigured()) {
			return new VoyageReranker(ragProperties, rerankProperties);
		}
		return new IdentityReranker();
	}

}
