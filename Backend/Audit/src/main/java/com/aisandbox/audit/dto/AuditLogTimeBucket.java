package com.aisandbox.audit.dto;

import java.time.Instant;

/**
 * One bucket of the events-over-time aggregation: the truncated bucket start
 * (e.g. the top of an hour or midnight of a day) and how many matching rows fall
 * inside it. Used as a JPA Criteria constructor-result, so the constructor
 * signature must match the {@code cb.construct(...)} selection in
 * {@link com.aisandbox.audit.service.AuditLogService}. Empty buckets are not
 * returned — the client zero-fills the gaps for charting.
 *
 * @param bucket start instant of the time bucket
 * @param count  number of rows whose {@code createdAt} falls in the bucket
 */
public record AuditLogTimeBucket(Instant bucket, long count) {
}
