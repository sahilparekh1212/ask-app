package com.aisandbox.audit.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Auditing columns shared by all entities, populated automatically by Spring Data JPA.
 *
 * <p>{@code createdByRequestId}/{@code updatedByRequestId} hold the request-correlation
 * UUID supplied by the UI ({@code X-Request-Id}) — NOT a user identity — so no personally
 * identifiable information is persisted. All fields are server-managed and exposed
 * read-only in the API (they appear in responses, never in request bodies).
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AuditableEntity {

	@CreatedDate
	@Column(name = "created_at", updatable = false)
	@Schema(accessMode = Schema.AccessMode.READ_ONLY)
	private Instant createdAt;

	@LastModifiedDate
	@Column(name = "updated_at")
	@Schema(accessMode = Schema.AccessMode.READ_ONLY)
	private Instant updatedAt;

	@CreatedBy
	@Column(name = "created_by_request_id", updatable = false)
	@Schema(accessMode = Schema.AccessMode.READ_ONLY)
	private String createdByRequestId;

	@LastModifiedBy
	@Column(name = "updated_by_request_id")
	@Schema(accessMode = Schema.AccessMode.READ_ONLY)
	private String updatedByRequestId;

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public String getCreatedByRequestId() {
		return createdByRequestId;
	}

	public String getUpdatedByRequestId() {
		return updatedByRequestId;
	}

}
