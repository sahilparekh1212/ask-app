package com.askapp.auth.exception;

import com.askapp.common.ratelimit.ActiveRequestRegistry;
import com.askapp.common.ratelimit.RateLimitInterceptor;
import com.askapp.common.ratelimit.RateLimitProperties;
import com.askapp.common.ratelimit.RequestDiscardedException;
import io.sentry.Sentry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.slf4j.MDC;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
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
		// client; writing the 429 body then would append a second JSON document to the bytes
		// already on the wire. Same guard as Audit's handler.
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
		NoResourceFoundException ex = new NoResourceFoundException(HttpMethod.GET, "/auth/nope");

		ResponseEntity<Map<String, Object>> response = handler.handleNoResource(ex);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(response.getBody())
			.containsEntry("status", 404)
			.containsEntry("error", "Not Found");
	}

	@Test
	void handleValidation_returns400WithTheFieldErrorMessage() throws NoSuchMethodException {
		BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "refreshRequest");
		bindingResult.addError(new FieldError("refreshRequest", "refreshToken", "must not be blank"));
		MethodParameter parameter = new MethodParameter(getClass().getDeclaredMethod("dummyTarget"), -1);
		MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parameter, bindingResult);

		ResponseEntity<Map<String, Object>> response = handler.handleValidation(ex);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody())
			.containsEntry("status", 400)
			.containsEntry("error", "Bad Request")
			.containsEntry("message", "refreshToken must not be blank");
	}

	@SuppressWarnings("unused")
	private void dummyTarget() {
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
		when(request.getRequestURI()).thenReturn("/auth/refresh");
		try (MockedStatic<Sentry> sentry = mockStatic(Sentry.class)) {
			interceptor.preHandle(request, mock(HttpServletResponse.class), new Object());
			registry.register("anonymous|POST|/auth/refresh"); // supersedes and discards the request above
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
