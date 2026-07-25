package com.askapp.audit.repository;

import com.askapp.audit.model.AiTrace;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Round-trips an {@link AiTrace} through the real Liquibase-created {@code ai_trace} table (changeset
 * 007) on H2 — proving the entity↔column mapping and the {@code @CreationTimestamp} stamp, and that
 * the {@code v_ai_trace_daily} view (changeset 008) applies. Because the changeset is dialect-neutral,
 * a green H2 round-trip is strong evidence the same schema works on the Postgres deployment.
 */
@DataJpaTest
class AiTraceRepositoryIntegrationTest {

	@Autowired
	private AiTraceRepository repository;

	@Autowired
	private TestEntityManager entityManager;

	@Test
	void persistsAndReadsBackAFullTraceStampingCreatedAt() {
		AiTrace trace = new AiTrace();
		trace.setFeature("CHAT");
		trace.setQueryText("how does auth work?");
		trace.setModel("claude-opus-4-8");
		trace.setReranked(true);
		trace.setCandidateCount(20);
		trace.setRetrievedCount(5);
		trace.setTopScore(0.87);
		trace.setRetrievedJson("[{\"rank\":1,\"source\":\"docs/adr/0001.md\",\"score\":0.87}]");
		trace.setCandidatesJson("[{\"rank\":1,\"source\":\"docs/adr/0001.md\",\"score\":0.5}]");
		trace.setReplyText("Auth issues RSA-signed JWTs.");
		trace.setReplyLength(28);
		trace.setBlocked(false);
		trace.setRetrievalMs(120L);
		trace.setLlmMs(2100L);
		trace.setInputTokens(1500);
		trace.setOutputTokens(180);
		trace.setAdmin(true);
		trace.setAuditGrounded(false);

		Long id = repository.saveAndFlush(trace).getId();
		entityManager.clear(); // read back from the DB, not the persistence context

		AiTrace read = repository.findById(id).orElseThrow();
		assertThat(read.getCreatedAt()).isNotNull(); // stamped by @CreationTimestamp on insert
		assertThat(read.getFeature()).isEqualTo("CHAT");
		assertThat(read.getQueryText()).isEqualTo("how does auth work?");
		assertThat(read.getModel()).isEqualTo("claude-opus-4-8");
		assertThat(read.isReranked()).isTrue();
		assertThat(read.getCandidateCount()).isEqualTo(20);
		assertThat(read.getRetrievedCount()).isEqualTo(5);
		assertThat(read.getTopScore()).isEqualTo(0.87);
		assertThat(read.getRetrievedJson()).contains("docs/adr/0001.md");
		assertThat(read.getCandidatesJson()).contains("docs/adr/0001.md");
		assertThat(read.getReplyText()).isEqualTo("Auth issues RSA-signed JWTs.");
		assertThat(read.getReplyLength()).isEqualTo(28);
		assertThat(read.isBlocked()).isFalse();
		assertThat(read.getRetrievalMs()).isEqualTo(120L);
		assertThat(read.getLlmMs()).isEqualTo(2100L);
		assertThat(read.getInputTokens()).isEqualTo(1500);
		assertThat(read.getOutputTokens()).isEqualTo(180);
		assertThat(read.isAdmin()).isTrue();
		assertThat(read.isAuditGrounded()).isFalse();
	}

	@Test
	void dailyViewAggregatesInteractionsByFeature() {
		AiTrace trace = new AiTrace();
		trace.setFeature("MCP_SEARCH");
		trace.setTopScore(0.9);
		trace.setRetrievalMs(50L);
		repository.saveAndFlush(trace);

		Object count = entityManager.getEntityManager()
			.createNativeQuery("SELECT interaction_count FROM v_ai_trace_daily WHERE feature = 'MCP_SEARCH'")
			.getSingleResult();

		assertThat(((Number) count).intValue()).isEqualTo(1);
	}

}
