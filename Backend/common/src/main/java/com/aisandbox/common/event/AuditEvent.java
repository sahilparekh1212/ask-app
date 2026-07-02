package com.aisandbox.common.event;

import java.time.Instant;

/**
 * The Kafka message published to {@code audit.events} when an auditable action happens in Auth,
 * and consumed by Audit to persist an {@code AuditLog}.
 *
 * <p>Shared between the two services (rather than one JSON-compatible copy per service) via this
 * {@code :common} module. {@code eventId} is the idempotency key the consumer dedups on for
 * at-least-once delivery.
 *
 * @param eventId     unique id for this event (consumer dedup key)
 * @param entityType  the audited entity type (used as the Kafka partition key)
 * @param action      what happened, e.g. LOGIN / TOKEN_REFRESH / LOGOUT
 * @param details     free-form detail (e.g. the user id)
 * @param occurredAt  when the action happened
 */
public record AuditEvent(String eventId, String entityType, String action, String details, Instant occurredAt) {
}
