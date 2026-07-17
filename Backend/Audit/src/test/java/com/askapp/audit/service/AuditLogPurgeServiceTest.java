package com.askapp.audit.service;

import com.askapp.audit.repository.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditLogPurgeServiceTest {

	private final AuditLogRepository repository = mock(AuditLogRepository.class);
	private final AuditLogPurgeService service = new AuditLogPurgeService(repository);

	@Test
	void purgesRowsOlderThanTheRetentionWindow() {
		when(repository.deleteByCreatedAtBefore(any())).thenReturn(7);
		ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);

		Instant before = Instant.now().minus(Duration.ofDays(90));
		int removed = service.purgeOlderThan(90);
		Instant after = Instant.now().minus(Duration.ofDays(90));

		verify(repository).deleteByCreatedAtBefore(cutoff.capture());
		// The cutoff is "now minus 90 days", so it lands between the two references taken around it.
		assertThat(cutoff.getValue()).isBetween(before, after);
		assertThat(removed).isEqualTo(7);
	}

	@Test
	void nonPositiveRetentionIsANoOp_guardingAgainstWipingTheTable() {
		assertThat(service.purgeOlderThan(0)).isZero();
		verify(repository, never()).deleteByCreatedAtBefore(any());
	}
}
