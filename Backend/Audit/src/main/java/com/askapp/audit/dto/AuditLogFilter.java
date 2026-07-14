package com.askapp.audit.dto;

import java.time.Instant;

/**
 * Immutable filter criteria shared by the paginated search and the aggregation
 * endpoints, so both apply exactly the same predicates. Any field may be
 * {@code null} (or {@code false} for {@code includeDeleted}), meaning "no
 * constraint on this dimension".
 *
 * @param entityType      exact-match on the audited entity type, or null for any
 * @param action          exact-match on the action, or null for any
 * @param details         case-insensitive substring match on the details text, or null
 * @param from            inclusive lower bound on {@code createdAt}, or null
 * @param to              exclusive upper bound on {@code createdAt}, or null
 * @param includeDeleted  when false (default) soft-deleted rows are excluded
 */
public record AuditLogFilter(
	String entityType,
	String action,
	String details,
	Instant from,
	Instant to,
	boolean includeDeleted
) {

	/** Convenience constructor for callers with no details constraint. */
	public AuditLogFilter(String entityType, String action, Instant from, Instant to, boolean includeDeleted) {
		this(entityType, action, null, from, to, includeDeleted);
	}
}
