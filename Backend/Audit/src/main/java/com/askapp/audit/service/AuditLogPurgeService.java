package com.askapp.audit.service;

import com.askapp.audit.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Retention purge for the audit trail: permanently removes audit rows older than the retention
 * window. Audit logs accumulate indefinitely otherwise (every login, refresh, chat, RAG and MCP
 * call writes one), so a bounded window keeps the table — and its indexes — from growing without
 * limit. Purge is a hard delete (not the soft-delete flag used for user-initiated removals): the
 * point is to reclaim storage, so the rows must actually leave the table.
 *
 * <p>Reference data is deliberately <em>not</em> purged — it is current, append-only master data,
 * not a time-series trail.
 */
@Service
public class AuditLogPurgeService {

	private static final Logger log = LoggerFactory.getLogger(AuditLogPurgeService.class);

	private final AuditLogRepository repository;

	public AuditLogPurgeService(AuditLogRepository repository) {
		this.repository = repository;
	}

	/**
	 * Delete audit rows created more than {@code retentionDays} ago. Returns the number removed.
	 * Non-positive retention is treated as "purge disabled" and removes nothing, guarding against a
	 * misconfiguration wiping the whole table.
	 */
	@Transactional
	public int purgeOlderThan(int retentionDays) {
		if (retentionDays < 1) {
			log.warn("Audit purge skipped: non-positive retentionDays={}", retentionDays);
			return 0;
		}
		Instant cutoff = Instant.now().minus(Duration.ofDays(retentionDays));
		int removed = repository.deleteByCreatedAtBefore(cutoff);
		log.info("Audit purge: removed {} rows older than {} days (created before {})",
			removed, retentionDays, cutoff);
		return removed;
	}
}
