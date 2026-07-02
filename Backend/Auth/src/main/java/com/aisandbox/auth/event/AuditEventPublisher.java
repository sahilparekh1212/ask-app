package com.aisandbox.auth.event;

import com.aisandbox.common.event.AuditEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Publishes audit events to Kafka.
 *
 * <p>Fire-and-forget and {@code @Async}: a slow or unavailable broker must never block or fail an
 * auth request, because authentication should not be coupled to the audit pipeline's availability.
 * Delivery failures are logged, not propagated.
 */
@Service
public class AuditEventPublisher {

	/** Topic the Audit service consumes from. */
	public static final String TOPIC = "audit.events";

	private static final Logger log = LoggerFactory.getLogger(AuditEventPublisher.class);

	private final KafkaTemplate<String, AuditEvent> kafkaTemplate;

	public AuditEventPublisher(KafkaTemplate<String, AuditEvent> kafkaTemplate) {
		this.kafkaTemplate = kafkaTemplate;
	}

	@Async
	public void publish(String entityType, String action, String details) {
		AuditEvent event = new AuditEvent(UUID.randomUUID().toString(), entityType, action, details, Instant.now());
		// Key by entityType so all events for an entity land on the same partition (ordering).
		kafkaTemplate.send(TOPIC, entityType, event).whenComplete((result, ex) -> {
			if (ex != null) {
				log.warn("Failed to publish audit event id={} action={}: {}", event.eventId(), action, ex.toString());
			} else {
				log.debug("Published audit event id={} action={}", event.eventId(), action);
			}
		});
	}
}
