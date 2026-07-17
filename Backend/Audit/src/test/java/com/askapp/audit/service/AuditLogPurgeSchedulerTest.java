package com.askapp.audit.service;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AuditLogPurgeSchedulerTest {

	private final AuditLogPurgeService purgeService = mock(AuditLogPurgeService.class);

	@Test
	void purgeDelegatesTheConfiguredRetentionToTheService() {
		new AuditLogPurgeScheduler(purgeService, 90).purge();

		verify(purgeService).purgeOlderThan(90);
	}
}
