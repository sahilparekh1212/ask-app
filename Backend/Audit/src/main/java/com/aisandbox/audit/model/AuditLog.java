package com.aisandbox.audit.model;

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
 * </ul>
 * Hibernate emits these under {@code ddl-auto=update} (DEV/SIT/LOCAL); PROD/UAT run
 * {@code validate}, so the same indexes must be created by the schema migration.
 */
@Entity
@Table(name = "audit_logs", indexes = {
	@Index(name = "idx_audit_active_created", columnList = "deleted, created_at"),
	@Index(name = "idx_audit_entity_type_action", columnList = "entity_type, action")
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

	protected AuditLog() {
	}

	public AuditLog(String entityType, String action, String details) {
		this.entityType = entityType;
		this.action = action;
		this.details = details;
	}

	public Long getId() {
		return id;
	}

	public String getEntityType() {
		return entityType;
	}

	public void setEntityType(String entityType) {
		this.entityType = entityType;
	}

	public String getAction() {
		return action;
	}

	public void setAction(String action) {
		this.action = action;
	}

	public String getDetails() {
		return details;
	}

	public void setDetails(String details) {
		this.details = details;
	}

	public boolean isDeleted() {
		return deleted;
	}

	public void setDeleted(boolean deleted) {
		this.deleted = deleted;
	}

	// createdAt / updatedAt / createdByRequestId are inherited from AuditableEntity.

}
