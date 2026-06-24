package com.aisandbox.audit.exception;

import com.aisandbox.audit.ratelimit.RateLimitProperties;
import com.aisandbox.audit.ratelimit.RequestDiscardedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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
	 * A newer request superseded this one. Tell the client it may retry after the
	 * configured cool-down (HTTP 429 + Retry-After).
	 */
	@ExceptionHandler(RequestDiscardedException.class)
	public ResponseEntity<Map<String, Object>> handleDiscarded(RequestDiscardedException ex) {
		logException(ex);
		int retryAfter = rateLimitProperties.getRetryAfterSeconds();
		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
			.header(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter))
			.body(errorBody(429, "Too Many Requests", ex));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<Map<String, Object>> handleAll(Exception ex) {
		logException(ex);
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
		return Map.of(
			"timestamp", Instant.now().toString(),
			"status", status,
			"error", error,
			"message", ex.getMessage() != null ? ex.getMessage() : "",
			"requestId", MDC.get("requestId") != null ? MDC.get("requestId") : ""
		);
	}

}
