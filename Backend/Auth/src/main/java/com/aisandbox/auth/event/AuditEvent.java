package com.aisandbox.auth.event;

import java.time.Instant;

/**
 * The Kafka message published to {@code audit.events} when an auditable action happens in Auth.
 *
 * <p>Serialized as JSON, so the Audit consumer can deserialize into its own copy of this record
 * without a shared class (there is no shared module yet). {@code eventId} gives the consumer a
 * key for idempotent, at-least-once handling.
 *
 * @param eventId     unique id for this event (consumer dedup key)
 * @param entityType  the audited entity type (used as the Kafka partition key)
 * @param action      what happened, e.g. LOGIN / TOKEN_REFRESH / LOGOUT
 * @param details     free-form detail (e.g. the user id)
 * @param occurredAt  when the action happened
 */
public record AuditEvent(String eventId, String entityType, String action, String details, Instant occurredAt) {
}
