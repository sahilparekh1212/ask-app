package com.askapp.audit.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * A UI feature flag — one row per toggleable SPA feature. The Angular client reads the whole set
 * at startup ({@link com.askapp.audit.controller.FeatureFlagController}) to decide which major
 * features to show in this deployment (see ADR-0015). Deployment-scoped, not per-user: authorization
 * stays server-side (OAuth2 + {@code @PreAuthorize}); these flags only curate what the UI renders.
 *
 * <p>Read-only from the app — there is no write endpoint. Flip {@code enabled} directly in the
 * database (SQL/Adminer) or with a follow-up Liquibase changeset. Seeded ON by the changeset
 * {@code 009-create-feature-flags}; Hibernate runs {@code ddl-auto=none}, so the table and its
 * unique index on {@code flag_key} are created there, mirroring this entity.
 */
@Entity
@Table(name = "feature_flags", indexes = {
	@Index(name = "idx_feature_flag_key", columnList = "flag_key", unique = true)
})
public class FeatureFlag extends AuditableEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Schema(accessMode = Schema.AccessMode.READ_ONLY)
	private Long id;

	/** Stable key the SPA gates on (e.g. {@code chat}, {@code voice}); unique across the table. */
	@Column(name = "flag_key", nullable = false, unique = true)
	private String flagKey;

	/** Whether the feature is shown. */
	@Column(nullable = false)
	private boolean enabled;

	/** Human-readable note for operators (shown in docs/Adminer); not used by the client logic. */
	private String description;

	protected FeatureFlag() {
	}

	public FeatureFlag(String flagKey, boolean enabled, String description) {
		this.flagKey = flagKey;
		this.enabled = enabled;
		this.description = description;
	}

	public Long getId() {
		return id;
	}

	public String getFlagKey() {
		return flagKey;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public String getDescription() {
		return description;
	}

}
