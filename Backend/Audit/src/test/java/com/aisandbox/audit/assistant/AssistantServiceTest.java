package com.aisandbox.audit.assistant;

import com.aisandbox.audit.assistant.dto.ChatRequest;
import com.aisandbox.audit.assistant.dto.ChatResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AssistantServiceTest {

	private static final ChatRequest CLEAN = new ChatRequest("What does the audit service do?", null);

	private final PromptScreener screener = new PromptScreener();
	private final AssistantContextBuilder contextBuilder = mock(AssistantContextBuilder.class);
	private final LlmClient llmClient = mock(LlmClient.class);

	@BeforeEach
	void stubContext() {
		when(contextBuilder.buildSystemPrompt(anyBoolean())).thenReturn("system prompt");
	}

	private AssistantService service(String apiKey) {
		AssistantProperties properties = new AssistantProperties(apiKey, "claude-opus-4-8", 1024);
		return new AssistantService(properties, screener, contextBuilder, llmClient);
	}

	@Test
	void throwsUnavailableWhenNoApiKeyConfigured() {
		assertThatThrownBy(() -> service("").chat(CLEAN, false))
			.isInstanceOf(AssistantUnavailableException.class)
			.hasMessageContaining("ANTHROPIC_API_KEY");
		verifyNoInteractions(llmClient);
	}

	@Test
	void blockedInputNeverReachesTheProvider() {
		ChatRequest dirty = new ChatRequest("my password=hunter2 fails", null);

		ChatResponse response = service("key").chat(dirty, false);

		assertThat(response.blocked()).isTrue();
		assertThat(response.reply()).isEqualTo(AssistantService.BLOCKED_REPLY);
		verifyNoInteractions(llmClient);
	}

	@Test
	void cleanInputIsForwardedWithTheRoleScopedPrompt() {
		when(llmClient.complete(anyString(), anyList(), anyString())).thenReturn("It stores audit rows.");

		ChatResponse response = service("key").chat(CLEAN, true);

		assertThat(response.blocked()).isFalse();
		assertThat(response.reply()).isEqualTo("It stores audit rows.");
		verify(contextBuilder).buildSystemPrompt(true);
		verify(llmClient).complete(eq("system prompt"), eq(List.of()), eq(CLEAN.message()));
	}

	@Test
	void userRoleFlagIsPassedThroughToTheContextBuilder() {
		when(llmClient.complete(anyString(), anyList(), anyString())).thenReturn("ok");

		service("key").chat(CLEAN, false);

		verify(contextBuilder).buildSystemPrompt(false);
	}

	@Test
	void providerFailureSurfacesAs503NotRaw500() {
		when(llmClient.complete(anyString(), anyList(), anyString())).thenThrow(new RuntimeException("boom"));

		assertThatThrownBy(() -> service("key").chat(CLEAN, false))
			.isInstanceOf(AssistantUnavailableException.class)
			.hasMessageContaining("temporarily unavailable");
	}

}
