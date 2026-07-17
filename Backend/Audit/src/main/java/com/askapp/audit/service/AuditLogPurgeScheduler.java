package com.askapp.audit.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the audit-log retention purge on a schedule. Cadence and window are configurable:
 * {@code audit.purge.cron} (default 03:00 daily) and {@code audit.purge.retention-days} (default
 * 90 — three months). Disable with {@code audit.purge.enabled=false} (off in tests).
 */
@Component
@ConditionalOnProperty(name = "audit.purge.enabled", havingValue = "true", matchIfMissing = true)
public class AuditLogPurgeScheduler {

	private final AuditLogPurgeService purgeService;
	private final int retentionDays;

	public AuditLogPurgeScheduler(AuditLogPurgeService purgeService,
			@Value("${audit.purge.retention-days:90}") int retentionDays) {
		this.purgeService = purgeService;
		this.retentionDays = retentionDays;
	}

	@Scheduled(cron = "${audit.purge.cron:0 0 3 * * *}")
	public void purge() {
		purgeService.purgeOlderThan(retentionDays);
	}
}
