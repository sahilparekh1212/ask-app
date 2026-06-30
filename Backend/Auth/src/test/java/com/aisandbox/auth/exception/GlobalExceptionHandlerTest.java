package com.aisandbox.auth.exception;

import com.aisandbox.auth.ratelimit.RateLimitProperties;
import com.aisandbox.auth.ratelimit.RequestDiscardedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

	private final RateLimitProperties properties = new RateLimitProperties();
	private final GlobalExceptionHandler handler = new GlobalExceptionHandler(properties);

	@AfterEach
	void clearMdc() {
		MDC.clear();
	}

	@Test
	void handleNotFound_returns404WithRequestIdFromMdc() {
		MDC.put("requestId", "rid-1");

		ResponseEntity<Map<String, Object>> response = handler.handleNotFound(new ResourceNotFoundException("missing 7"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(response.getBody())
			.containsEntry("status", 404)
			.containsEntry("error", "Not Found")
			.containsEntry("message", "missing 7")
			.containsEntry("requestId", "rid-1");
	}

	@Test
	void handleDiscarded_returns429WithRetryAfterHeader() {
		properties.setRetryAfterSeconds(42);

		ResponseEntity<Map<String, Object>> response = handler.handleDiscarded(new RequestDiscardedException("k1"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
		assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("42");
		assertThat(response.getBody())
			.containsEntry("status", 429)
			.containsEntry("error", "Too Many Requests");
	}

	@Test
	void handleAll_returns500AndBlankMessageAndRequestIdWhenAbsent() {
		ResponseEntity<Map<String, Object>> response = handler.handleAll(new RuntimeException());

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(response.getBody())
			.containsEntry("status", 500)
			.containsEntry("message", "")
			.containsEntry("requestId", "");
	}
}
