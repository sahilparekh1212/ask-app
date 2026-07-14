package com.askapp.audit.event;

import com.askapp.common.event.AuditEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Publishes audit events for this service's own AI features (chat, RAG search, MCP tools) to the
 * same {@code audit.events} topic Auth publishes login events to. They then flow
 * back through Audit's own {@link com.askapp.audit.event.AuditEventConsumer} and land as
 * {@code AuditLog} rows — so every feature emits a domain event and Audit is the single sink,
 * whether the producer is another service or this one.
 *
 * <p>Publishing through the broker (rather than saving the row inline) is deliberate: it keeps
 * one uniform event-sourcing path for all producers and exercises the Kafka pipeline for real
 * feature traffic. The posture matches Auth's and ADR-0006's — fire-and-forget and {@code @Async}
 * so a slow or unavailable broker never blocks or fails a feature request; a lost audit row on a
 * broker outage is the accepted at-most-once tradeoff. Delivery failures are logged, not
 * propagated.
 */
@Service
public class AuditEventPublisher {

	/** Topic the {@link AuditEventConsumer} reads from. */
	public static final String TOPIC = "audit.events";

	private static final Logger log = LoggerFactory.getLogger(AuditEventPublisher.class);

	private final KafkaTemplate<String, AuditEvent> kafkaTemplate;

	public AuditEventPublisher(KafkaTemplate<String, AuditEvent> kafkaTemplate) {
		this.kafkaTemplate = kafkaTemplate;
	}

	/**
	 * @param entityType the feature emitting the event (e.g. {@code Assistant}, {@code Rag},
	 *                   {@code Mcp}) — also the Kafka partition key
	 * @param action     what happened (e.g. {@code CHAT}, {@code SEARCH},
	 *                   {@code TOOL_CALL})
	 * @param details    non-PII {@code key=value} detail only — never message text, query text, or
	 *                   any user content (see the assistant/RAG data-flow ADRs)
	 */
	@Async
	public void publish(String entityType, String action, String details) {
		AuditEvent event = new AuditEvent(UUID.randomUUID().toString(), entityType, action, details, Instant.now());
		// Key by entityType so all events for a feature land on the same partition (ordering).
		kafkaTemplate.send(TOPIC, entityType, event).whenComplete((result, ex) -> {
			if (ex != null) {
				log.warn("Failed to publish audit event id={} action={}: {}", event.eventId(), action, ex.toString());
			} else {
				log.debug("Published audit event id={} action={}", event.eventId(), action);
			}
		});
	}
}
