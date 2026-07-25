package com.askapp.audit.assistant;

import com.askapp.audit.assistant.dto.ChatTurn;
import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.models.messages.Usage;
import com.anthropic.services.blocking.MessageService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnthropicLlmClientTest {

	private final AssistantProperties properties = new AssistantProperties("test-key", "claude-opus-4-8", 1024);
	private final AnthropicClient sdkClient = mock(AnthropicClient.class);
	private final MessageService messageService = mock(MessageService.class);
	private final AnthropicLlmClient client = new AnthropicLlmClient(properties, sdkClient);

	@Test
	void buildsRequestFromScreenedInputsOnlyAndJoinsTextBlocksWithTokenUsage() {
		Message message = mock(Message.class);
		TextBlock textBlock = mock(TextBlock.class);
		var contentBlock = mock(com.anthropic.models.messages.ContentBlock.class);
		when(contentBlock.text()).thenReturn(Optional.of(textBlock));
		when(textBlock.text()).thenReturn("the answer");
		when(message.content()).thenReturn(List.of(contentBlock));
		Usage usage = mock(Usage.class);
		when(usage.inputTokens()).thenReturn(12L);
		when(usage.outputTokens()).thenReturn(34L);
		when(message.usage()).thenReturn(usage);
		when(sdkClient.messages()).thenReturn(messageService);
		ArgumentCaptor<MessageCreateParams> params = ArgumentCaptor.forClass(MessageCreateParams.class);
		when(messageService.create(params.capture())).thenReturn(message);

		LlmResult result = client.complete("system prompt",
			List.of(new ChatTurn("user", "hi"), new ChatTurn("assistant", "hello")), "what now?");

		assertThat(result.text()).isEqualTo("the answer");
		assertThat(result.inputTokens()).isEqualTo(12);
		assertThat(result.outputTokens()).isEqualTo(34);
		MessageCreateParams sent = params.getValue();
		assertThat(sent.model().toString()).isEqualTo("claude-opus-4-8");
		assertThat(sent.maxTokens()).isEqualTo(1024);
		// history turns + the new message — and nothing else (no headers, no JWT, no extras)
		assertThat(sent.messages()).hasSize(3);
	}

	@Test
	void tokenUsageIsBestEffortAndLeavesCountsNullWhenUnavailable() {
		Message message = mock(Message.class);
		TextBlock textBlock = mock(TextBlock.class);
		var contentBlock = mock(com.anthropic.models.messages.ContentBlock.class);
		when(contentBlock.text()).thenReturn(Optional.of(textBlock));
		when(textBlock.text()).thenReturn("ok");
		when(message.content()).thenReturn(List.of(contentBlock));
		// Provider omits usage — the trace-only token capture must degrade to null, not fail.
		when(message.usage()).thenThrow(new RuntimeException("no usage"));
		when(sdkClient.messages()).thenReturn(messageService);
		when(messageService.create(org.mockito.ArgumentMatchers.any(MessageCreateParams.class)))
			.thenReturn(message);

		LlmResult result = client.complete("system prompt", List.of(), "hi");

		assertThat(result.text()).isEqualTo("ok");
		assertThat(result.inputTokens()).isNull();
		assertThat(result.outputTokens()).isNull();
	}

	@Test
	void lazyClientIsBuiltOnceAndMemoized() {
		AnthropicLlmClient lazy = new AnthropicLlmClient(properties);

		AnthropicClient first = lazy.getClient();
		AnthropicClient second = lazy.getClient();

		assertThat(first).isNotNull().isSameAs(second);
	}

}
