package com.aisandbox.audit.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * An audit log row. Indexes back the query API in
 * {@link com.aisandbox.audit.controller.AuditLogController}:
 * <ul>
 *   <li>{@code idx_audit_active_created} — the default listing (active rows, newest first)
 *       and {@code createdAt} range filters; leading {@code deleted} lets the optimizer skip
 *       soft-deleted rows.</li>
 *   <li>{@code idx_audit_entity_type_action} — equality filters on {@code entityType}
 *       (and {@code entityType}+{@code action} together), which also serve the grouped
 *       aggregation in {@code /stats}.</li>
 *   <li>{@code idx_audit_event_id} — unique index on the source Kafka {@code eventId}, giving
 *       the {@code AuditEventConsumer} idempotent (at-least-once) inserts. Null for rows created
 *       via REST; most DBs allow many NULLs in a unique index.</li>
 * </ul>
 * Hibernate emits these under {@code ddl-auto=update} (DEV/SIT/LOCAL); PROD/UAT run
 * {@code validate}, so the same indexes must be created by the schema migration.
 */
@Entity
@Table(name = "audit_logs", indexes = {
	@Index(name = "idx_audit_active_created", columnList = "deleted, created_at"),
	@Index(name = "idx_audit_entity_type_action", columnList = "entity_type, action"),
	@Index(name = "idx_audit_event_id", columnList = "event_id", unique = true)
})
public class AuditLog extends AuditableEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Schema(accessMode = Schema.AccessMode.READ_ONLY)
	private Long id;

	private String entityType;

	private String action;

	private String details;

	@Schema(accessMode = Schema.AccessMode.READ_ONLY)
	private boolean deleted = false;

	/** Source Kafka event id when this row was created by the consumer; null for REST-created rows. */
	@Column(name = "event_id")
	@Schema(accessMode = Schema.AccessMode.READ_ONLY)
	private String eventId;

	protected AuditLog() {
	}

	@JsonCreator
	public AuditLog(@JsonProperty("entityType") String entityType, @JsonProperty("action") String action,
			@JsonProperty("details") String details) {
		this(entityType, action, details, null);
	}

	/** Used by {@code AuditEventConsumer} to stamp the source Kafka event id at creation time. */
	public AuditLog(String entityType, String action, String details, String eventId) {
		this.entityType = entityType;
		this.action = action;
		this.details = details;
		this.eventId = eventId;
	}

	public Long getId() {
		return id;
	}

	public String getEntityType() {
		return entityType;
	}

	public String getAction() {
		return action;
	}

	public String getDetails() {
		return details;
	}

	public boolean isDeleted() {
		return deleted;
	}

	/** One-way transition: soft-delete is the only mutation this entity allows post-creation. */
	public void markDeleted() {
		this.deleted = true;
	}

	public String getEventId() {
		return eventId;
	}

	// createdAt / updatedAt / createdByRequestId are inherited from AuditableEntity.

}
