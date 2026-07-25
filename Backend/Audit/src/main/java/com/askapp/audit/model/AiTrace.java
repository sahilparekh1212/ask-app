package com.askapp.audit.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * One AI interaction trace (ADR-0014): a chat turn or an MCP search, captured for answer-quality
 * analysis — the query, the pre/post-rerank candidates (as JSON), the model, the reply, per-stage
 * latencies and token usage. Deliberately holds <em>no user identity</em> (only an {@code admin}
 * role flag); it measures the system, not the user. Persisted best-effort and asynchronously by
 * {@code AiTraceService}, so it never sits on the request path.
 *
 * <p>{@code createdAt} is stamped by Hibernate on insert ({@link CreationTimestamp}) rather than
 * Spring Data auditing, because the write runs on a background thread where the request-scoped
 * auditor (MDC) isn't available.
 */
@Entity
@Table(name = "ai_trace", indexes = {
	@Index(name = "idx_ai_trace_feature_created", columnList = "feature, created_at"),
	@Index(name = "idx_ai_trace_created_at", columnList = "created_at")
})
public class AiTrace {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@CreationTimestamp
	@Column(name = "created_at", updatable = false)
	private Instant createdAt;

	/** {@code CHAT} or {@code MCP_SEARCH}. */
	private String feature;

	@Column(name = "query_text")
	private String queryText;

	private String model;

	private boolean reranked;

	/** Size of the first-stage (pre-rerank) candidate pool. */
	@Column(name = "candidate_count")
	private int candidateCount;

	/** Size of the returned (post-rerank) result set. */
	@Column(name = "retrieved_count")
	private int retrievedCount;

	/** Best result's relevance score — a grounding-strength signal (null when nothing retrieved). */
	@Column(name = "top_score")
	private Double topScore;

	/** JSON: the post-rerank results as {@code [{rank, source, heading, score}]}. */
	@Column(name = "retrieved_json")
	private String retrievedJson;

	/** JSON: the pre-rerank candidate pool as {@code [{rank, source, score}]}. */
	@Column(name = "candidates_json")
	private String candidatesJson;

	@Column(name = "reply_text")
	private String replyText;

	@Column(name = "reply_length")
	private Integer replyLength;

	private boolean blocked;

	@Column(name = "blocked_category")
	private String blockedCategory;

	@Column(name = "retrieval_ms")
	private Long retrievalMs;

	@Column(name = "llm_ms")
	private Long llmMs;

	@Column(name = "input_tokens")
	private Integer inputTokens;

	@Column(name = "output_tokens")
	private Integer outputTokens;

	private boolean admin;

	@Column(name = "audit_grounded")
	private boolean auditGrounded;

	public Long getId() {
		return id;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public String getFeature() {
		return feature;
	}

	public void setFeature(String feature) {
		this.feature = feature;
	}

	public String getQueryText() {
		return queryText;
	}

	public void setQueryText(String queryText) {
		this.queryText = queryText;
	}

	public String getModel() {
		return model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public boolean isReranked() {
		return reranked;
	}

	public void setReranked(boolean reranked) {
		this.reranked = reranked;
	}

	public int getCandidateCount() {
		return candidateCount;
	}

	public void setCandidateCount(int candidateCount) {
		this.candidateCount = candidateCount;
	}

	public int getRetrievedCount() {
		return retrievedCount;
	}

	public void setRetrievedCount(int retrievedCount) {
		this.retrievedCount = retrievedCount;
	}

	public Double getTopScore() {
		return topScore;
	}

	public void setTopScore(Double topScore) {
		this.topScore = topScore;
	}

	public String getRetrievedJson() {
		return retrievedJson;
	}

	public void setRetrievedJson(String retrievedJson) {
		this.retrievedJson = retrievedJson;
	}

	public String getCandidatesJson() {
		return candidatesJson;
	}

	public void setCandidatesJson(String candidatesJson) {
		this.candidatesJson = candidatesJson;
	}

	public String getReplyText() {
		return replyText;
	}

	public void setReplyText(String replyText) {
		this.replyText = replyText;
	}

	public Integer getReplyLength() {
		return replyLength;
	}

	public void setReplyLength(Integer replyLength) {
		this.replyLength = replyLength;
	}

	public boolean isBlocked() {
		return blocked;
	}

	public void setBlocked(boolean blocked) {
		this.blocked = blocked;
	}

	public String getBlockedCategory() {
		return blockedCategory;
	}

	public void setBlockedCategory(String blockedCategory) {
		this.blockedCategory = blockedCategory;
	}

	public Long getRetrievalMs() {
		return retrievalMs;
	}

	public void setRetrievalMs(Long retrievalMs) {
		this.retrievalMs = retrievalMs;
	}

	public Long getLlmMs() {
		return llmMs;
	}

	public void setLlmMs(Long llmMs) {
		this.llmMs = llmMs;
	}

	public Integer getInputTokens() {
		return inputTokens;
	}

	public void setInputTokens(Integer inputTokens) {
		this.inputTokens = inputTokens;
	}

	public Integer getOutputTokens() {
		return outputTokens;
	}

	public void setOutputTokens(Integer outputTokens) {
		this.outputTokens = outputTokens;
	}

	public boolean isAdmin() {
		return admin;
	}

	public void setAdmin(boolean admin) {
		this.admin = admin;
	}

	public boolean isAuditGrounded() {
		return auditGrounded;
	}

	public void setAuditGrounded(boolean auditGrounded) {
		this.auditGrounded = auditGrounded;
	}

}
