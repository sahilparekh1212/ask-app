package com.askapp.audit.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Body of {@code POST /api/v1/audit-logs/demo}. A missing {@code count} deserializes to 0 and
 * fails the {@code @Min}, so the field is effectively required without a separate null check.
 *
 * @param count how many demo rows to generate, 1..500 (capped so a typo can't flood the table)
 */
public record DemoDataRequest(
	@Min(value = 1, message = "count must be at least 1")
	@Max(value = 500, message = "count must be at most 500")
	int count
) {
}
