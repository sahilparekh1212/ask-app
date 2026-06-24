package com.aisandbox.audit.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import io.swagger.v3.oas.annotations.media.Schema;

@Entity
@Table(name = "audit_logs")
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
