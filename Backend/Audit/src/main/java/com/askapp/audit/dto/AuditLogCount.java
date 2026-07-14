package com.askapp.audit.dto;

/**
 * One bucket of a grouped count, e.g. {@code ("CREATE", 42)} when grouping by
 * action. Used as a JPA Criteria constructor-result, so the constructor
 * signature must match the {@code cb.construct(...)} selection in
 * {@link com.askapp.audit.service.AuditLogService}.
 *
 * @param key   the group value (an action or an entityType)
 * @param count the number of rows in that group
 */
public record AuditLogCount(String key, long count) {
}
