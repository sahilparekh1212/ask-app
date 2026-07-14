package com.askapp.audit.assistant.dto;

/**
 * Response of {@code POST /api/v1/assistant/chat}.
 *
 * @param reply   the assistant's answer (or the canned guardrail message when blocked)
 * @param blocked true when the server-side screen rejected the input and nothing was sent
 *                to the LLM provider
 */
public record ChatResponse(String reply, boolean blocked) {

	public static ChatResponse of(String reply) {
		return new ChatResponse(reply, false);
	}

	public static ChatResponse blockedBy(String reply) {
		return new ChatResponse(reply, true);
	}
}
