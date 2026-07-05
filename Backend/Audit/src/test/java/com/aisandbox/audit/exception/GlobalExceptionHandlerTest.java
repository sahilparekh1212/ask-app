package com.aisandbox.audit.exception;

import com.aisandbox.common.ratelimit.ActiveRequestRegistry;
import com.aisandbox.common.ratelimit.RateLimitInterceptor;
import com.aisandbox.common.ratelimit.RateLimitProperties;
import com.aisandbox.common.ratelimit.RequestDiscardedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
	void handleNoResource_returns404ForUnknownPaths() {
		NoResourceFoundException ex = new NoResourceFoundException(HttpMethod.GET, "/api/audit-logs");

		ResponseEntity<Map<String, Object>> response = handler.handleNoResource(ex);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(response.getBody())
			.containsEntry("status", 404)
			.containsEntry("error", "Not Found");
	}

	@Test
	void handleAccessDenied_returns403NotAServerError() {
		ResponseEntity<Map<String, Object>> response = handler.handleAccessDenied(new AccessDeniedException("nope"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(response.getBody())
			.containsEntry("status", 403)
			.containsEntry("error", "Forbidden")
			.containsEntry("message", "nope");
	}

	@Test
	void handleAssistantUnavailable_returns503() {
		ResponseEntity<Map<String, Object>> response = handler.handleAssistantUnavailable(
			new com.aisandbox.audit.assistant.AssistantUnavailableException("no key"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
		assertThat(response.getBody())
			.containsEntry("status", 503)
			.containsEntry("error", "Service Unavailable")
			.containsEntry("message", "no key");
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

	@Test
	void handleAll_returns429NotA500WhenTheCurrentRequestWasSuperseded() throws Exception {
		// A superseded request whose worker thread was interrupted mid-work can throw a generic
		// exception; the handler must surface that as 429 (graceful shedding), not a 500. Drive
		// this through the real RateLimitInterceptor/ActiveRequestRegistry (public API) rather
		// than reaching into common.ratelimit's package-private internals from this package.
		ActiveRequestRegistry registry = new ActiveRequestRegistry();
		RateLimitInterceptor interceptor = new RateLimitInterceptor(registry, properties);
		HttpServletRequest request = mock(HttpServletRequest.class);
		when(request.getMethod()).thenReturn("POST");
		when(request.getRequestURI()).thenReturn("/api/v1/audit-logs");
		try {
			interceptor.preHandle(request, mock(HttpServletResponse.class), new Object());
			registry.register("anonymous|POST|/api/v1/audit-logs"); // supersedes and discards the request above
			Thread.interrupted(); // clear the interrupt discard() raised; not under test

			ResponseEntity<Map<String, Object>> response = handler.handleAll(new RuntimeException("interrupted mid-save"));

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
			assertThat(response.getBody()).containsEntry("status", 429);
		} finally {
			interceptor.afterCompletion(request, mock(HttpServletResponse.class), new Object(), null);
		}
	}
}
