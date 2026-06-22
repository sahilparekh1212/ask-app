package com.aisandbox.audit.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "audit_logs")
public class AuditLog {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String entityType;

	private String action;

	private String details;

	private Instant createdAt;

	protected AuditLog() {
	}

	public AuditLog(String entityType, String action, String details) {
		this.entityType = entityType;
		this.action = action;
		this.details = details;
		this.createdAt = Instant.now();
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

	public Instant getCreatedAt() {
		return createdAt;
	}

}
