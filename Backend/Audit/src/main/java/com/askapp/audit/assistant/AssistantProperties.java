package com.askapp.audit.assistant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Configuration for the LLM chat assistant. The API key is server-side only (env var
 * {@code ANTHROPIC_API_KEY}) — it is never exposed to the SPA, which talks exclusively to
 * our own {@link AssistantController} proxy. When no key is configured the feature degrades
 * to 503 rather than failing startup, so every other endpoint keeps working key-less.
 */
@Component
public class AssistantProperties {

	private final String apiKey;
	private final String model;
	private final long maxTokens;

	public AssistantProperties(
			@Value("${assistant.api-key:}") String apiKey,
			@Value("${assistant.model:claude-opus-4-8}") String model,
			@Value("${assistant.max-tokens:8192}") long maxTokens) {
		this.apiKey = apiKey;
		this.model = model;
		this.maxTokens = maxTokens;
	}

	public String getApiKey() {
		return apiKey;
	}

	public String getModel() {
		return model;
	}

	public long getMaxTokens() {
		return maxTokens;
	}

	/** True when an API key is present, i.e. the assistant can actually call the provider. */
	public boolean isConfigured() {
		return apiKey != null && !apiKey.isBlank();
	}

}
