package com.askapp.audit.assistant;

import com.askapp.audit.assistant.dto.ChatTurn;
import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.TextBlock;
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
	void buildsRequestFromScreenedInputsOnlyAndJoinsTextBlocks() {
		Message message = mock(Message.class);
		TextBlock textBlock = mock(TextBlock.class);
		var contentBlock = mock(com.anthropic.models.messages.ContentBlock.class);
		when(contentBlock.text()).thenReturn(Optional.of(textBlock));
		when(textBlock.text()).thenReturn("the answer");
		when(message.content()).thenReturn(List.of(contentBlock));
		when(sdkClient.messages()).thenReturn(messageService);
		ArgumentCaptor<MessageCreateParams> params = ArgumentCaptor.forClass(MessageCreateParams.class);
		when(messageService.create(params.capture())).thenReturn(message);

		String reply = client.complete("system prompt",
			List.of(new ChatTurn("user", "hi"), new ChatTurn("assistant", "hello")), "what now?");

		assertThat(reply).isEqualTo("the answer");
		MessageCreateParams sent = params.getValue();
		assertThat(sent.model().toString()).isEqualTo("claude-opus-4-8");
		assertThat(sent.maxTokens()).isEqualTo(1024);
		// history turns + the new message — and nothing else (no headers, no JWT, no extras)
		assertThat(sent.messages()).hasSize(3);
	}

	@Test
	void lazyClientIsBuiltOnceAndMemoized() {
		AnthropicLlmClient lazy = new AnthropicLlmClient(properties);

		AnthropicClient first = lazy.getClient();
		AnthropicClient second = lazy.getClient();

		assertThat(first).isNotNull().isSameAs(second);
	}

}
