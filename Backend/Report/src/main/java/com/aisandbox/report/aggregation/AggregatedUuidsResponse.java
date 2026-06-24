package com.aisandbox.report.aggregation;

import java.util.List;

/**
 * Request-correlation UUIDs (createdByRequestId) of all transaction, audit and notification
 * records — including soft-deleted ones — across all accounts.
 */
public record AggregatedUuidsResponse(
		List<String> transactionRequestIds,
		List<String> auditRequestIds,
		List<String> notificationRequestIds) {
}
