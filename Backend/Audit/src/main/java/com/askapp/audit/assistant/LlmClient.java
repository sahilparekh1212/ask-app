package com.askapp.audit.assistant;

import com.askapp.audit.assistant.dto.ChatTurn;

import java.util.List;

/**
 * The single seam through which the assistant talks to an LLM provider. Keeping this an
 * interface makes the guardrail logic in {@link AssistantService} unit-testable without
 * network access, and confines provider-SDK types to one implementation class.
 */
public interface LlmClient {

	/**
	 * Sends one grounded conversation to the model and returns its text reply.
	 *
	 * @param systemPrompt the role-scoped system prompt from {@link AssistantContextBuilder}
	 * @param history      prior turns, oldest first (already screened)
	 * @param message      the user's new message (already screened)
	 */
	String complete(String systemPrompt, List<ChatTurn> history, String message);

}
