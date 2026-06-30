package com.aisandbox.audit.event;

import java.time.Instant;

/**
 * The Kafka message consumed from {@code audit.events} (produced by the Auth service).
 *
 * <p>Deserialized from JSON into this Audit-owned copy of the record — there is no shared module,
 * and the consumer is configured to ignore producer type headers, so the two services only share
 * the JSON shape, not a class. {@code eventId} is the idempotency key for at-least-once delivery.
 */
public record AuditEvent(String eventId, String entityType, String action, String details, Instant occurredAt) {
}
