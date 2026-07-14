package com.askapp.auth.exception;

import com.askapp.common.ratelimit.ActiveRequest;
import com.askapp.common.ratelimit.DiscardContext;
import com.askapp.common.ratelimit.RateLimitProperties;
import com.askapp.common.ratelimit.RequestDiscardedException;
import io.sentry.Sentry;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	private final RateLimitProperties rateLimitProperties;

	public GlobalExceptionHandler(RateLimitProperties rateLimitProperties) {
		this.rateLimitProperties = rateLimitProperties;
	}

	@ExceptionHandler(ResourceNotFoundException.class)
	public ResponseEntity<Map<String, Object>> handleNotFound(ResourceNotFoundException ex) {
		logException(ex);
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorBody(404, "Not Found", ex));
	}

	/**
	 * Unknown route / missing static resource. Without this, the catch-all below would turn a
	 * mistyped or unversioned URL into a misleading 500 instead of a 404.
	 */
	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<Map<String, Object>> handleNoResource(NoResourceFoundException ex) {
		logException(ex);
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorBody(404, "Not Found", ex));
	}

	/**
	 * A newer request superseded this one. Tell the client it may retry after the
	 * configured cool-down (HTTP 429 + Retry-After).
	 */
	@ExceptionHandler(RequestDiscardedException.class)
	public ResponseEntity<Map<String, Object>> handleDiscarded(
			RequestDiscardedException ex, HttpServletResponse response) {
		logException(ex);
		// The discard interrupt can land after the superseded request's 200 response has already
		// been committed (body flushed to the client). Writing the 429 body then would APPEND a
		// second JSON document to bytes already on the wire — the client has a complete response,
		// so write nothing (null = handled, no body). Same fix as Audit's handler, where a k6 run
		// against the compose stack surfaced corrupted `{...body}{...429 body}` payloads.
		if (response.isCommitted()) {
			return null;
		}
		// Not committed: drop any partial body the aborted handler buffered so the 429 JSON
		// replaces it instead of trailing it.
		response.resetBuffer();
		int retryAfter = rateLimitProperties.getRetryAfterSeconds();
		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
			.header(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter))
			.body(errorBody(429, "Too Many Requests", ex));
	}

	/** A {@code @Valid}-annotated {@code @RequestBody} failed constraint validation (e.g. a blank refresh token). */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
		logException(ex);
		String message = ex.getBindingResult().getFieldErrors().stream()
			.map(fe -> fe.getField() + " " + fe.getDefaultMessage())
			.collect(Collectors.joining("; "));
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorBody(400, "Bad Request", message));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<Map<String, Object>> handleAll(Exception ex, HttpServletResponse response) {
		// A superseded (rate-limited) request can fail mid-work because the "newest wins" limiter
		// interrupted its worker thread (e.g. during the DB write). Surface that as a 429 like any
		// other discard, not a misleading 500.
		ActiveRequest current = DiscardContext.current();
		if (current != null && current.isDiscarded()) {
			return handleDiscarded(new RequestDiscardedException(current.getKey()), response);
		}
		logException(ex);
		// Report to Sentry from HERE, not just via the SDK's SentryExceptionResolver: that resolver
		// runs at LOWEST_PRECEDENCE, but this @ControllerAdvice resolves the exception first and stops
		// DispatcherServlet's resolver chain, so the SDK would never see a single controller 500.
		// Only genuine unexpected 500s reach this catch-all (the 400/403/404/429 paths have their own
		// handlers), so this is exactly the right signal. No-op when SENTRY_DSN is unset.
		Sentry.captureException(ex);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBody(500, "Internal Server Error", ex));
	}

	private void logException(Exception ex) {
		String originMethod = ex.getStackTrace().length > 0 ? ex.getStackTrace()[0].getMethodName() : "unknown";
		String stackSnippet = Arrays.stream(ex.getStackTrace())
			.map(StackTraceElement::toString)
			.collect(Collectors.joining("\n"));
		String truncated = stackSnippet.length() > 300 ? stackSnippet.substring(0, 300) : stackSnippet;
		log.error("{} threw {}: {} | stack: {}", originMethod, ex.getClass().getName(), ex.getMessage(), truncated);
	}

	private Map<String, Object> errorBody(int status, String error, Exception ex) {
		return errorBody(status, error, ex.getMessage());
	}

	private Map<String, Object> errorBody(int status, String error, String message) {
		return Map.of(
			"timestamp", Instant.now().toString(),
			"status", status,
			"error", error,
			"message", message != null ? message : "",
			"requestId", MDC.get("requestId") != null ? MDC.get("requestId") : ""
		);
	}

}
