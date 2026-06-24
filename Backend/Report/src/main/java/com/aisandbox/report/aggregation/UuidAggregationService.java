package com.aisandbox.report.aggregation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Aggregates the request-correlation UUIDs ({@code createdByRequestId}) of all transaction,
 * audit and notification records across the platform, including soft-deleted ones.
 *
 * <p>Each downstream service is called with {@code ?includeDeleted=true}; the caller's JWT and
 * {@code X-Request-Id} are propagated so the calls authenticate and stay correlated. Failures
 * to reach a service degrade gracefully to an empty list rather than failing the whole report.
 */
@Service
public class UuidAggregationService {

	private static final Logger log = LoggerFactory.getLogger(UuidAggregationService.class);
	private static final ParameterizedTypeReference<List<Map<String, Object>>> RECORD_LIST =
		new ParameterizedTypeReference<>() {};

	private final RestClient transactionClient;
	private final RestClient auditClient;
	private final RestClient notificationClient;

	public UuidAggregationService(
			@Value("${transaction.service.url:http://localhost:8082}") String transactionUrl,
			@Value("${audit.service.url:http://localhost:8083}") String auditUrl,
			@Value("${notification.service.url:http://localhost:8084}") String notificationUrl) {
		this.transactionClient = RestClient.builder().baseUrl(transactionUrl).build();
		this.auditClient = RestClient.builder().baseUrl(auditUrl).build();
		this.notificationClient = RestClient.builder().baseUrl(notificationUrl).build();
	}

	public AggregatedUuidsResponse aggregate(String authHeader, String requestId) {
		return new AggregatedUuidsResponse(
			fetchRequestIds(transactionClient, "/api/transactions", authHeader, requestId),
			fetchRequestIds(auditClient, "/api/audit-logs", authHeader, requestId),
			fetchRequestIds(notificationClient, "/api/notifications", authHeader, requestId));
	}

	private List<String> fetchRequestIds(RestClient client, String path, String authHeader, String requestId) {
		try {
			List<Map<String, Object>> records = client.get()
				.uri(uri -> uri.path(path).queryParam("includeDeleted", true).build())
				.headers(h -> {
					if (authHeader != null) h.set("Authorization", authHeader);
					if (requestId != null) h.set("X-Request-Id", requestId);
				})
				.retrieve()
				.body(RECORD_LIST);
			if (records == null) {
				return List.of();
			}
			return records.stream()
				.map(record -> record.get("createdByRequestId"))
				.filter(Objects::nonNull)
				.map(Object::toString)
				.distinct()
				.toList();
		} catch (Exception e) {
			log.warn("Failed to fetch request ids from {}: {}", path, e.getMessage());
			return List.of();
		}
	}

}
