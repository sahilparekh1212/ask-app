package com.askapp.audit.dto;

import java.util.List;

/**
 * Aggregated view of the audit logs matching a filter: the total matching count
 * plus per-action and per-entityType breakdowns (each ordered by descending
 * count). All three honour the same {@link AuditLogFilter} as the search endpoint.
 *
 * @param total         total rows matching the filter
 * @param byAction      counts grouped by action, highest first
 * @param byEntityType  counts grouped by entityType, highest first
 */
public record AuditLogStats(
	long total,
	List<AuditLogCount> byAction,
	List<AuditLogCount> byEntityType
) {
}
