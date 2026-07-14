package com.askapp.audit.assistant;

import com.askapp.audit.assistant.dto.ChatTurn;
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.ThinkingConfigAdaptive;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

/**
 * {@link LlmClient} backed by the official Anthropic Java SDK. The HTTP client is built
 * lazily on first use so the application starts (and every non-assistant endpoint works)
 * without an API key; {@link AssistantService} never calls this when unconfigured.
 *
 * <p>Note what is deliberately <em>not</em> here: nothing from the inbound HTTP request —
 * no {@code Authorization} header, no cookies, no JWT — can reach the provider, because the
 * outbound request is built exclusively from the screened message, the screened history and
 * the server-assembled system prompt.
 */
@Component
public class AnthropicLlmClient implements LlmClient {

	private final AssistantProperties properties;
	private volatile AnthropicClient client;

	// @Autowired disambiguates for Spring — with two constructors present, container wiring
	// must use this one (the other exists only so tests can inject a mock SDK client).
	@Autowired
	public AnthropicLlmClient(AssistantProperties properties) {
		this.properties = properties;
	}

	/** Test constructor: inject a pre-built (mock) SDK client. */
	AnthropicLlmClient(AssistantProperties properties, AnthropicClient client) {
		this.properties = properties;
		this.client = client;
	}

	@Override
	public String complete(String systemPrompt, List<ChatTurn> history, String message) {
		MessageCreateParams.Builder params = MessageCreateParams.builder()
			.model(properties.getModel())
			.maxTokens(properties.getMaxTokens())
			.thinking(ThinkingConfigAdaptive.builder().build())
			.system(systemPrompt);
		for (ChatTurn turn : history) {
			if ("assistant".equals(turn.role())) {
				params.addAssistantMessage(turn.content());
			} else {
				params.addUserMessage(turn.content());
			}
		}
		params.addUserMessage(message);

		Message response = getClient().messages().create(params.build());
		return response.content().stream()
			.flatMap(block -> block.text().stream())
			.map(text -> text.text())
			.collect(Collectors.joining());
	}

	// Package-private so tests can cover the lazy build/memoization without a network call
	// (constructing the OkHttp-backed client performs no I/O).
	AnthropicClient getClient() {
		AnthropicClient existing = client;
		if (existing != null) {
			return existing;
		}
		synchronized (this) {
			if (client == null) {
				client = AnthropicOkHttpClient.builder()
					.apiKey(properties.getApiKey())
					.timeout(Duration.ofSeconds(120))
					.build();
			}
			return client;
		}
	}

}
