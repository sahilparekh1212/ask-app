package com.aisandbox.auth.event;

import com.aisandbox.common.event.AuditEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditEventPublisherTest {

	@SuppressWarnings("unchecked")
	private final KafkaTemplate<String, AuditEvent> template = mock(KafkaTemplate.class);
	private final AuditEventPublisher publisher = new AuditEventPublisher(template);

	@Test
	@SuppressWarnings("unchecked")
	void publish_sendsAnEventKeyedByEntityTypeToTheAuditTopic() {
		when(template.send(eq(AuditEventPublisher.TOPIC), eq("User"), any(AuditEvent.class)))
			.thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

		publisher.publish("User", "LOGIN", "demo-user");

		ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
		verify(template).send(eq(AuditEventPublisher.TOPIC), eq("User"), captor.capture());
		AuditEvent event = captor.getValue();
		assertThat(event.entityType()).isEqualTo("User");
		assertThat(event.action()).isEqualTo("LOGIN");
		assertThat(event.details()).isEqualTo("demo-user");
		assertThat(event.eventId()).isNotBlank();
		assertThat(event.occurredAt()).isNotNull();
	}

	@Test
	void publish_swallowsBrokerFailuresInsteadOfPropagating() {
		when(template.send(eq(AuditEventPublisher.TOPIC), eq("User"), any(AuditEvent.class)))
			.thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));

		// Fire-and-forget: a failed send must never throw back to the auth request.
		assertThatCode(() -> publisher.publish("User", "LOGIN", "demo-user")).doesNotThrowAnyException();

		verify(template).send(eq(AuditEventPublisher.TOPIC), eq("User"), any(AuditEvent.class));
	}
}
