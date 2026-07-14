package com.askapp.audit.assistant;

import com.askapp.audit.assistant.dto.ChatRequest;
import com.askapp.audit.assistant.dto.ChatResponse;
import com.askapp.audit.event.AuditEventPublisher;
import com.askapp.audit.rag.RagService;
import com.askapp.audit.rag.ScoredChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AssistantServiceTest {

	// A design/how-it-works question — not about live activity, so no audit data is attached.
	private static final ChatRequest CLEAN = new ChatRequest("What does the audit service do?", null);
	// A question about the running system's state — the detector flags it, so audit data attaches.
	private static final ChatRequest STATE = new ChatRequest("How many logins were there today?", null);

	private final PromptScreener screener = new PromptScreener();
	private final AssistantContextBuilder contextBuilder = mock(AssistantContextBuilder.class);
	private final LlmClient llmClient = mock(LlmClient.class);
	private final RagService ragService = mock(RagService.class);
	private final AuditEventPublisher auditEventPublisher = mock(AuditEventPublisher.class);

	@BeforeEach
	void stubContext() {
		when(contextBuilder.buildSystemPrompt(anyBoolean(), anyList(), anyBoolean())).thenReturn("system prompt");
		// Default: RAG not ready (unconfigured/empty index) — chat works exactly as pre-RAG.
		when(ragService.isReady()).thenReturn(false);
	}

	private AssistantService service(String apiKey) {
		AssistantProperties properties = new AssistantProperties(apiKey, "claude-opus-4-8", 1024);
		return new AssistantService(properties, screener, contextBuilder, new AuditQueryDetector(),
			llmClient, ragService, auditEventPublisher);
	}

	@Test
	void throwsUnavailableWhenNoApiKeyConfigured() {
		assertThatThrownBy(() -> service("").chat(CLEAN, false))
			.isInstanceOf(AssistantUnavailableException.class)
			.hasMessageContaining("ANTHROPIC_API_KEY");
		verifyNoInteractions(llmClient);
		// Nothing happened, so no audit event either.
		verifyNoInteractions(auditEventPublisher);
	}

	@Test
	void blockedInputNeverReachesTheProvider() {
		ChatRequest dirty = new ChatRequest("my password=hunter2 fails", null);

		ChatResponse response = service("key").chat(dirty, false);

		assertThat(response.blocked()).isTrue();
		assertThat(response.reply()).isEqualTo(AssistantService.BLOCKED_REPLY);
		verifyNoInteractions(llmClient);
		// The screener runs before retrieval, so a blocked message never reaches the
		// embeddings provider either.
		verifyNoInteractions(ragService);
	}

	@Test
	void blockedTurnEmitsAScreenerCategoryButNoContent() {
		ChatRequest dirty = new ChatRequest("my password=hunter2 fails", null);

		service("key").chat(dirty, false);

		ArgumentCaptor<String> details = ArgumentCaptor.forClass(String.class);
		verify(auditEventPublisher).publish(eq("Assistant"), eq("CHAT"), details.capture());
		assertThat(details.getValue()).contains("blocked=true").contains("category=");
		// The matched sensitive value must never leak into the audit detail.
		assertThat(details.getValue()).doesNotContain("hunter2");
	}

	@Test
	void cleanInputIsForwardedWithTheRoleScopedPrompt() {
		when(llmClient.complete(anyString(), anyList(), anyString())).thenReturn("It stores audit rows.");

		ChatResponse response = service("key").chat(CLEAN, true);

		assertThat(response.blocked()).isFalse();
		assertThat(response.reply()).isEqualTo("It stores audit rows.");
		// CLEAN is a design question → not about state → no live audit data attached.
		verify(contextBuilder).buildSystemPrompt(true, List.of(), false);
		verify(llmClient).complete(eq("system prompt"), eq(List.of()), eq(CLEAN.message()));
	}

	@Test
	void successfulChatEmitsANonPiiChatEvent() {
		List<ScoredChunk> chunks = List.of(new ScoredChunk("docs/adr/0001.md", "Decision", "text", 0.9));
		when(ragService.isReady()).thenReturn(true);
		when(ragService.search(eq(CLEAN.message()), isNull())).thenReturn(chunks);
		when(llmClient.complete(anyString(), anyList(), anyString())).thenReturn("grounded reply");

		service("key").chat(CLEAN, false);

		ArgumentCaptor<String> details = ArgumentCaptor.forClass(String.class);
		verify(auditEventPublisher).publish(eq("Assistant"), eq("CHAT"), details.capture());
		assertThat(details.getValue())
			.contains("blocked=false")
			.contains("model=claude-opus-4-8")
			.contains("retrievedChunks=1")
			.contains("auditGrounded=false")
			.contains("latencyMs=");
		// No message or reply text in the audit detail.
		assertThat(details.getValue()).doesNotContain(CLEAN.message()).doesNotContain("grounded reply");
	}

	@Test
	void providerFailureEmitsNoChatEvent() {
		when(llmClient.complete(anyString(), anyList(), anyString())).thenThrow(new RuntimeException("boom"));

		assertThatThrownBy(() -> service("key").chat(CLEAN, false))
			.isInstanceOf(AssistantUnavailableException.class);

		verifyNoInteractions(auditEventPublisher);
	}

	@Test
	void userRoleFlagIsPassedThroughToTheContextBuilder() {
		when(llmClient.complete(anyString(), anyList(), anyString())).thenReturn("ok");

		service("key").chat(CLEAN, false);

		verify(contextBuilder).buildSystemPrompt(eq(false), anyList(), anyBoolean());
	}

	@Test
	void genericQuestionDoesNotAttachLiveAuditData() {
		when(llmClient.complete(anyString(), anyList(), anyString())).thenReturn("ok");

		service("key").chat(CLEAN, false);

		// "What does the audit service do?" is about design, not activity → includeAuditData=false.
		verify(contextBuilder).buildSystemPrompt(eq(false), anyList(), eq(false));
	}

	@Test
	void stateQuestionAttachesLiveAuditData() {
		when(llmClient.complete(anyString(), anyList(), anyString())).thenReturn("42 logins.");

		service("key").chat(STATE, true);

		// "How many logins were there today?" is about live state → includeAuditData=true.
		verify(contextBuilder).buildSystemPrompt(eq(true), anyList(), eq(true));
		ArgumentCaptor<String> details = ArgumentCaptor.forClass(String.class);
		verify(auditEventPublisher).publish(eq("Assistant"), eq("CHAT"), details.capture());
		assertThat(details.getValue()).contains("auditGrounded=true");
	}

	@Test
	void retrievedChunksAreAppendedToThePromptWhenRagIsReady() {
		List<ScoredChunk> chunks = List.of(new ScoredChunk("docs/adr/0001.md", "Decision", "text", 0.9));
		when(ragService.isReady()).thenReturn(true);
		when(ragService.search(eq(CLEAN.message()), isNull())).thenReturn(chunks);
		when(llmClient.complete(anyString(), anyList(), anyString())).thenReturn("grounded reply");

		service("key").chat(CLEAN, false);

		verify(contextBuilder).buildSystemPrompt(false, chunks, false);
	}

	@Test
	void retrievalFailureDegradesToTheUnretrievedPrompt() {
		when(ragService.isReady()).thenReturn(true);
		when(ragService.search(anyString(), any())).thenThrow(new RuntimeException("provider down"));
		when(llmClient.complete(anyString(), anyList(), anyString())).thenReturn("still works");

		ChatResponse response = service("key").chat(CLEAN, false);

		assertThat(response.reply()).isEqualTo("still works");
		verify(contextBuilder).buildSystemPrompt(false, List.of(), false);
	}

	@Test
	void providerFailureSurfacesAs503NotRaw500() {
		when(llmClient.complete(anyString(), anyList(), anyString())).thenThrow(new RuntimeException("boom"));

		assertThatThrownBy(() -> service("key").chat(CLEAN, false))
			.isInstanceOf(AssistantUnavailableException.class)
			.hasMessageContaining("temporarily unavailable");
	}

}
