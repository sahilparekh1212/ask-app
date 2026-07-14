package com.askapp.audit.event;

import com.askapp.audit.model.AuditLog;
import com.askapp.audit.repository.AuditLogRepository;
import com.askapp.common.event.AuditEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditEventConsumerTest {

	private final AuditLogRepository repository = mock(AuditLogRepository.class);
	private final AuditEventConsumer consumer = new AuditEventConsumer(repository);

	@Test
	void onAuditEvent_persistsANewEventAsAnAuditLog() {
		AuditEvent event = new AuditEvent("evt-1", "User", "LOGIN", "demo-user", Instant.now());
		when(repository.existsByEventId("evt-1")).thenReturn(false);

		consumer.onAuditEvent(event);

		ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
		verify(repository).save(captor.capture());
		AuditLog saved = captor.getValue();
		assertThat(saved.getEntityType()).isEqualTo("User");
		assertThat(saved.getAction()).isEqualTo("LOGIN");
		assertThat(saved.getDetails()).isEqualTo("demo-user");
		assertThat(saved.getEventId()).isEqualTo("evt-1");
	}

	@Test
	void onAuditEvent_skipsAnAlreadyProcessedEvent() {
		AuditEvent event = new AuditEvent("evt-1", "User", "LOGIN", "demo-user", Instant.now());
		when(repository.existsByEventId("evt-1")).thenReturn(true);

		consumer.onAuditEvent(event);

		verify(repository, never()).save(any());
	}

	@Test
	void onAuditEvent_persistsWithoutADedupCheckWhenEventIdIsNull() {
		AuditEvent event = new AuditEvent(null, "User", "LOGOUT", null, Instant.now());

		consumer.onAuditEvent(event);

		verify(repository).save(any(AuditLog.class));
		verify(repository, never()).existsByEventId(any());
	}
}
