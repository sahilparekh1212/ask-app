package com.askapp.audit.trace;

import com.askapp.audit.assistant.LlmResult;
import com.askapp.audit.model.AiTrace;
import com.askapp.audit.rag.RagService;
import com.askapp.audit.rag.ScoredChunk;
import com.askapp.audit.repository.AiTraceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AiTraceServiceTest {

	private final AiTraceRepository repository = mock(AiTraceRepository.class);
	private final ObjectMapper objectMapper = new ObjectMapper();

	private static RagService.Retrieval retrieval() {
		return new RagService.Retrieval(
			List.of(new ScoredChunk("docs/adr/0001.md", "Decision", "body", 0.95),
				new ScoredChunk("Audit/src/main/java/.../RagService.java", "javadoc", "body2", 0.80)),
			List.of(new ScoredChunk("docs/adr/0001.md", "Decision", "body", 0.5),
				new ScoredChunk("other.md", "H", "z", 0.4),
				new ScoredChunk("Audit/src/main/java/.../RagService.java", "javadoc", "body2", 0.3)));
	}

	private static AiTraceRecord chatRecord() {
		return AiTraceRecord.chat("the query", retrieval(), "claude-opus-4-8", "the reply",
			new LlmResult("the reply", 10, 20), 12L, 34L, true, false);
	}

	private AiTraceService service(boolean enabled, boolean captureContent) {
		return new AiTraceService(repository, new AiTraceProperties(enabled, captureContent), objectMapper);
	}

	private AiTrace saved(AiTraceService service, AiTraceRecord record) {
		service.record(record);
		ArgumentCaptor<AiTrace> captor = ArgumentCaptor.forClass(AiTrace.class);
		verify(repository).save(captor.capture());
		return captor.getValue();
	}

	@Test
	void mapsAFullChatRecordWithContentAndBothRerankStages() {
		AiTrace trace = saved(service(true, true), chatRecord());

		assertThat(trace.getId()).isNull();
		assertThat(trace.getCreatedAt()).isNull(); // stamped by Hibernate on persist, not here
		assertThat(trace.getFeature()).isEqualTo("CHAT");
		assertThat(trace.getQueryText()).isEqualTo("the query");
		assertThat(trace.getModel()).isEqualTo("claude-opus-4-8");
		assertThat(trace.getReplyText()).isEqualTo("the reply");
		assertThat(trace.getReplyLength()).isEqualTo("the reply".length());
		assertThat(trace.getInputTokens()).isEqualTo(10);
		assertThat(trace.getOutputTokens()).isEqualTo(20);
		assertThat(trace.getRetrievalMs()).isEqualTo(12L);
		assertThat(trace.getLlmMs()).isEqualTo(34L);
		assertThat(trace.isAdmin()).isTrue();
		assertThat(trace.isAuditGrounded()).isFalse();
		assertThat(trace.isBlocked()).isFalse();
		assertThat(trace.getBlockedCategory()).isNull();
		assertThat(trace.getRetrievedCount()).isEqualTo(2);
		assertThat(trace.getCandidateCount()).isEqualTo(3);
		// Candidate pool (3) is wider than the returned results (2) → reranking was applied.
		assertThat(trace.isReranked()).isTrue();
		assertThat(trace.getTopScore()).isEqualTo(0.95);
		assertThat(trace.getRetrievedJson()).contains("docs/adr/0001.md").contains("\"rank\":1");
		assertThat(trace.getCandidatesJson()).contains("other.md");
	}

	@Test
	void contentOffDropsRawTextButKeepsRetrievalScores() {
		AiTrace trace = saved(service(true, false), chatRecord());

		assertThat(trace.getQueryText()).isNull();
		assertThat(trace.getReplyText()).isNull();
		// Scores/ranks/sources are public corpus metadata — still captured.
		assertThat(trace.getTopScore()).isEqualTo(0.95);
		assertThat(trace.getRetrievedJson()).isNotBlank();
		assertThat(trace.getReplyLength()).isEqualTo("the reply".length());
	}

	@Test
	void blockedRecordCarriesTheCategoryAndNoRetrieval() {
		AiTrace trace = saved(service(true, true), AiTraceRecord.blockedChat("bad message", false, "jwt"));

		assertThat(trace.isBlocked()).isTrue();
		assertThat(trace.getBlockedCategory()).isEqualTo("jwt");
		assertThat(trace.getQueryText()).isEqualTo("bad message");
		assertThat(trace.getRetrievedCount()).isZero();
		assertThat(trace.getCandidateCount()).isZero();
		assertThat(trace.getTopScore()).isNull();
		assertThat(trace.getRetrievedJson()).isNull();
	}

	@Test
	void disabledIsANoOp() {
		service(false, true).record(chatRecord());

		verifyNoInteractions(repository);
	}

	@Test
	void persistFailureIsSwallowed() {
		when(repository.save(org.mockito.ArgumentMatchers.any())).thenThrow(new RuntimeException("db down"));

		// Must not propagate — tracing is best-effort and never breaks the observed request.
		service(true, true).record(chatRecord());

		verify(repository).save(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void queryTextIsTruncatedToTheColumnWidth() {
		String hugeQuery = "x".repeat(AiTraceService.MAX_QUERY + 500);
		AiTraceRecord record = AiTraceRecord.chat(hugeQuery, retrieval(), "m", "r",
			new LlmResult("r", 1, 1), 1L, 1L, false, false);

		AiTrace trace = saved(service(true, true), record);

		assertThat(trace.getQueryText()).hasSize(AiTraceService.MAX_QUERY);
	}

}
