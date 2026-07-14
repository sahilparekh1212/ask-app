package com.askapp.audit.assistant.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Body of {@code POST /api/v1/assistant/chat}. History is capped so a client can't stuff an
 * unbounded prompt through the proxy; both the message and every history turn are screened
 * server-side before anything is sent to the LLM provider.
 *
 * @param message the user's new question (required)
 * @param history prior turns, oldest first (optional, max 20)
 */
public record ChatRequest(
	@NotBlank @Size(max = 2000) String message,
	@Valid @Size(max = 20) List<ChatTurn> history
) {

	public List<ChatTurn> historyOrEmpty() {
		return history != null ? history : List.of();
	}
}
