package com.askapp.common.ratelimit;

/**
 * Thrown when an in-flight request is superseded by a newer request for the same
 * (user, endpoint) key. Mapped to HTTP 429 + {@code Retry-After} by the global
 * exception handler.
 */
public class RequestDiscardedException extends RuntimeException {

	public RequestDiscardedException(String key) {
		super("Request superseded by a newer request for: " + key);
	}

}
