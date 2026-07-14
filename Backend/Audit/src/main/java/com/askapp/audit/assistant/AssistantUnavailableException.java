package com.askapp.audit.assistant;

/**
 * The assistant can't serve the request right now — either no API key is configured on the
 * server, or the LLM provider call failed. Mapped to HTTP 503 by the global handler.
 */
public class AssistantUnavailableException extends RuntimeException {

	public AssistantUnavailableException(String message) {
		super(message);
	}

	public AssistantUnavailableException(String message, Throwable cause) {
		super(message, cause);
	}

}
