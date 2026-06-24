package com.aisandbox.notification.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Forwards audit events to the standalone Audit service (POST /api/audit-logs).
 *
 * <p>The caller's {@code Authorization} (user JWT) and {@code X-Request-Id} are propagated so
 * the Audit service authenticates the call and stamps the row with the originating request
 * UUID. Only non-PII fields are sent (resource path, HTTP method, status/outcome). Failures
 * are swallowed (logged at WARN) so auditing never breaks the user's request.
 */
@Component
public class AuditPublisher {

	private static final Logger log = LoggerFactory.getLogger(AuditPublisher.class);

	private final RestClient client;
	private final boolean enabled;

	public AuditPublisher(@Value("${audit.service.url:}") String auditServiceUrl,
			@Value("${audit.publish.enabled:true}") boolean enabled) {
		this.enabled = enabled && auditServiceUrl != null && !auditServiceUrl.isBlank();
		this.client = RestClient.builder().baseUrl(auditServiceUrl == null ? "" : auditServiceUrl).build();
	}

	public void publish(String entityType, String action, String details, String authHeader, String requestId) {
		if (!enabled) {
			return;
		}
		try {
			client.post().uri("/api/audit-logs")
				.headers(h -> {
					if (authHeader != null) h.set("Authorization", authHeader);
					if (requestId != null) h.set("X-Request-Id", requestId);
				})
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("entityType", entityType, "action", action, "details", details))
				.retrieve()
				.toBodilessEntity();
		} catch (Exception e) {
			log.warn("Failed to publish audit event to audit-service: {}", e.getMessage());
		}
	}

}
