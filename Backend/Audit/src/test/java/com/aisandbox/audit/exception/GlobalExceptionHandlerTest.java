package com.aisandbox.audit.exception;

import com.aisandbox.common.ratelimit.ActiveRequestRegistry;
import com.aisandbox.common.ratelimit.RateLimitInterceptor;
import com.aisandbox.common.ratelimit.RateLimitProperties;
import com.aisandbox.common.ratelimit.RequestDiscardedException;
import io.sentry.Sentry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
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

		ResponseEntity<Map<String, Object>> response =
			handler.handleDiscarded(new RequestDiscardedException("k1"), new MockHttpServletResponse());

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
		assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("42");
		assertThat(response.getBody())
			.containsEntry("status", 429)
			.containsEntry("error", "Too Many Requests");
	}

	@Test
	void handleDiscarded_writesNothingOntoAnAlreadyCommittedResponse() {
		// The discard interrupt can land after the superseded request's 200 was flushed to the
		// client. Writing the 429 body then would append a second JSON document to the bytes
		// already on the wire (seen as `{...stats}{...429}` payloads under a real k6 run).
		MockHttpServletResponse committed = new MockHttpServletResponse();
		committed.setCommitted(true);

		ResponseEntity<Map<String, Object>> response =
			handler.handleDiscarded(new RequestDiscardedException("k1"), committed);

		assertThat(response).isNull();
	}

	@Test
	void handleDiscarded_dropsABufferedPartialBodySoThe429ReplacesIt() throws Exception {
		MockHttpServletResponse uncommitted = new MockHttpServletResponse();
		uncommitted.getWriter().print("{\"partial\":");

		ResponseEntity<Map<String, Object>> response =
			handler.handleDiscarded(new RequestDiscardedException("k1"), uncommitted);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
		assertThat(uncommitted.getContentAsString()).isEmpty();
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
	void handleTypeMismatch_returns400NotA500ForABadQueryParam() {
		// e.g. GET /stats/timeline?interval=week — the enum conversion fails before the
		// controller runs; a client typo must read as their error, not a server fault.
		MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException(
			"week", com.aisandbox.audit.dto.TimelineInterval.class, "interval", null,
			new IllegalArgumentException("no enum constant"));

		ResponseEntity<Map<String, Object>> response = handler.handleTypeMismatch(ex);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody())
			.containsEntry("status", 400)
			.containsEntry("error", "Bad Request")
			.containsEntry("message", "Invalid value for parameter 'interval'");
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
		ResponseEntity<Map<String, Object>> response =
			handler.handleAll(new RuntimeException(), new MockHttpServletResponse());

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(response.getBody())
			.containsEntry("status", 500)
			.containsEntry("message", "")
			.containsEntry("requestId", "");
	}

	@Test
	void handleAll_reportsTheUnexpectedExceptionToSentry() {
		// The catch-all must capture explicitly: the SDK's own SentryExceptionResolver runs at
		// LOWEST_PRECEDENCE, but this @ControllerAdvice resolves the exception first and stops
		// DispatcherServlet's resolver chain, so without this the SDK sees no controller 500.
		RuntimeException boom = new RuntimeException("boom");
		try (MockedStatic<Sentry> sentry = mockStatic(Sentry.class)) {
			handler.handleAll(boom, new MockHttpServletResponse());
			sentry.verify(() -> Sentry.captureException(boom));
		}
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
		try (MockedStatic<Sentry> sentry = mockStatic(Sentry.class)) {
			interceptor.preHandle(request, mock(HttpServletResponse.class), new Object());
			registry.register("anonymous|POST|/api/v1/audit-logs"); // supersedes and discards the request above
			Thread.interrupted(); // clear the interrupt discard() raised; not under test

			ResponseEntity<Map<String, Object>> response =
				handler.handleAll(new RuntimeException("interrupted mid-save"), new MockHttpServletResponse());

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
			assertThat(response.getBody()).containsEntry("status", 429);
			// A graceful rate-limit shed is expected load-shedding, not an error — it must NOT page Sentry.
			sentry.verify(() -> Sentry.captureException(any()), never());
		} finally {
			interceptor.afterCompletion(request, mock(HttpServletResponse.class), new Object(), null);
		}
	}
}
