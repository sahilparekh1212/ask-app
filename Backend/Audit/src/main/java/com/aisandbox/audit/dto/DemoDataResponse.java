package com.aisandbox.audit.dto;

/**
 * Result of {@code POST /api/v1/audit-logs/demo}.
 *
 * @param created how many demo rows were inserted
 */
public record DemoDataResponse(int created) {
}
