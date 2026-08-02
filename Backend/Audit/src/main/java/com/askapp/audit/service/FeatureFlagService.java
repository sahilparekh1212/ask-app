package com.askapp.audit.service;

import com.askapp.audit.dto.FeatureFlagResponse;
import com.askapp.audit.repository.FeatureFlagRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Read side for UI feature flags (see ADR-0015). The SPA calls this once at startup to learn which
 * features to show. There is no write path — flags are flipped in the database (SQL/Adminer or a
 * Liquibase changeset). The table is tiny (a handful of rows) and read once per session, so a plain
 * repository read per call is fine — no caching (Spring Cache is not used anywhere in this service).
 */
@Service
public class FeatureFlagService {

	private final FeatureFlagRepository repository;

	public FeatureFlagService(FeatureFlagRepository repository) {
		this.repository = repository;
	}

	/** Every flag, key-ordered, as the client-facing DTO. */
	public List<FeatureFlagResponse> list() {
		return repository.findAllByOrderByFlagKeyAsc().stream()
			.map(FeatureFlagResponse::from)
			.toList();
	}
}
