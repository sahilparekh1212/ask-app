package com.aisandbox.audit.assistant;

import com.aisandbox.audit.assistant.dto.FlashcardDeck;
import com.aisandbox.audit.event.AuditEventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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

class FlashcardServiceTest {

	private final AssistantContextBuilder contextBuilder = mock(AssistantContextBuilder.class);
	private final LlmClient llmClient = mock(LlmClient.class);
	private final ObjectMapper objectMapper = new ObjectMapper();
	private final AuditEventPublisher auditEventPublisher = mock(AuditEventPublisher.class);

	@BeforeEach
	void stubContext() {
		when(contextBuilder.groundingContext(anyBoolean())).thenReturn("\n<app_docs>docs</app_docs>\n");
	}

	private FlashcardService service(String apiKey) {
		AssistantProperties properties = new AssistantProperties(apiKey, "claude-opus-4-8", 2048);
		return new FlashcardService(properties, contextBuilder, llmClient, objectMapper, auditEventPublisher);
	}

	@Test
	void throwsUnavailableWhenNoApiKey() {
		assertThatThrownBy(() -> service("").generate(5, false))
			.isInstanceOf(AssistantUnavailableException.class);
		verifyNoInteractions(llmClient);
		verifyNoInteractions(auditEventPublisher);
	}

	@Test
	void parsesACleanJsonDeck() {
		when(llmClient.complete(anyString(), anyList(), anyString())).thenReturn(
			"{\"cards\":[{\"question\":\"What is Audit?\",\"answer\":\"It stores audit rows.\"}]}");

		FlashcardDeck deck = service("key").generate(1, false);

		assertThat(deck.cards()).hasSize(1);
		assertThat(deck.cards().get(0).question()).isEqualTo("What is Audit?");
		verify(contextBuilder).groundingContext(false);
	}

	@Test
	void successfulGenerationEmitsANonPiiGeneratedEvent() {
		when(llmClient.complete(anyString(), anyList(), anyString())).thenReturn(
			"{\"cards\":[{\"question\":\"What is Audit?\",\"answer\":\"It stores audit rows.\"}]}");

		service("key").generate(3, false);

		ArgumentCaptor<String> details = ArgumentCaptor.forClass(String.class);
		verify(auditEventPublisher).publish(eq("Flashcards"), eq("GENERATED"), details.capture());
		assertThat(details.getValue())
			.contains("model=claude-opus-4-8")
			.contains("requested=3")
			.contains("produced=1")
			.contains("latencyMs=");
		// No card content in the audit detail.
		assertThat(details.getValue()).doesNotContain("What is Audit?").doesNotContain("stores audit rows");
	}

	@Test
	void providerFailureEmitsNoGeneratedEvent() {
		when(llmClient.complete(anyString(), anyList(), anyString())).thenThrow(new RuntimeException("boom"));

		assertThatThrownBy(() -> service("key").generate(3, false))
			.isInstanceOf(AssistantUnavailableException.class);

		verifyNoInteractions(auditEventPublisher);
	}

	@Test
	void adminFlagIsThreadedIntoTheGroundingContext() {
		when(llmClient.complete(anyString(), anyList(), anyString())).thenReturn(
			"{\"cards\":[{\"question\":\"q\",\"answer\":\"a\"}]}");

		service("key").generate(1, true);

		verify(contextBuilder).groundingContext(true);
	}

	@Test
	void stripsCodeFencesAndSurroundingProse() {
		when(llmClient.complete(anyString(), anyList(), anyString())).thenReturn(
			"Here you go:\n```json\n{\"cards\":[{\"question\":\"q\",\"answer\":\"a\"}]}\n```");

		FlashcardDeck deck = service("key").generate(1, false);

		assertThat(deck.cards()).hasSize(1);
	}

	@Test
	void dropsMalformedCardsMissingASide() {
		when(llmClient.complete(anyString(), anyList(), anyString())).thenReturn(
			"{\"cards\":[{\"question\":\"good\",\"answer\":\"a\"},{\"question\":\"\",\"answer\":\"b\"},"
				+ "{\"question\":\"no answer\"}]}");

		FlashcardDeck deck = service("key").generate(3, false);

		assertThat(deck.cards()).hasSize(1);
		assertThat(deck.cards().get(0).question()).isEqualTo("good");
	}

	@Test
	void unreadableReplyBecomes503() {
		when(llmClient.complete(anyString(), anyList(), anyString())).thenReturn("not json at all");

		assertThatThrownBy(() -> service("key").generate(3, false))
			.isInstanceOf(AssistantUnavailableException.class);
	}

	@Test
	void emptyDeckBecomes503() {
		when(llmClient.complete(anyString(), anyList(), anyString())).thenReturn("{\"cards\":[]}");

		assertThatThrownBy(() -> service("key").generate(3, false))
			.isInstanceOf(AssistantUnavailableException.class);
	}

	@Test
	void providerFailureBecomes503() {
		when(llmClient.complete(anyString(), anyList(), anyString())).thenThrow(new RuntimeException("boom"));

		assertThatThrownBy(() -> service("key").generate(3, false))
			.isInstanceOf(AssistantUnavailableException.class)
			.hasMessageContaining("temporarily unavailable");
	}

	@Test
	void promptCarriesFlashcardInstructionsAndGrounding() {
		when(llmClient.complete(anyString(), anyList(), anyString())).thenReturn(
			"{\"cards\":[{\"question\":\"q\",\"answer\":\"a\"}]}");

		service("key").generate(7, false);

		verify(llmClient).complete(
			org.mockito.ArgumentMatchers.contains("study flashcards"),
			eq(List.of()),
			org.mockito.ArgumentMatchers.contains("exactly 7"));
	}

}
