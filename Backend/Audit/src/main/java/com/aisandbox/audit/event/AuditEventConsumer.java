package com.aisandbox.audit.event;

import com.aisandbox.audit.model.AuditLog;
import com.aisandbox.audit.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumes audit events from Kafka and persists them as {@link AuditLog}s.
 *
 * <p>Delivery is at-least-once, so consumption is made idempotent by {@code eventId}: a
 * redelivered event whose id is already stored is skipped. Anything that throws here is retried
 * and finally dead-lettered to {@code audit.events.DLT} by the error handler in
 * {@link com.aisandbox.audit.config.KafkaConsumerConfig}.
 */
@Component
public class AuditEventConsumer {

	private static final Logger log = LoggerFactory.getLogger(AuditEventConsumer.class);

	private final AuditLogRepository repository;

	public AuditEventConsumer(AuditLogRepository repository) {
		this.repository = repository;
	}

	@KafkaListener(topics = "audit.events", groupId = "${spring.kafka.consumer.group-id:audit-service}")
	@Transactional
	public void onAuditEvent(AuditEvent event) {
		if (event.eventId() != null && repository.existsByEventId(event.eventId())) {
			log.debug("Skipping already-processed audit event id={}", event.eventId());
			return;
		}
		AuditLog auditLog = new AuditLog(event.entityType(), event.action(), event.details());
		auditLog.setEventId(event.eventId());
		repository.save(auditLog);
		log.info("Persisted audit log from event id={} action={}", event.eventId(), event.action());
	}
}
