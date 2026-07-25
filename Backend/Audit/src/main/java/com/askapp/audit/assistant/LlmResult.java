package com.askapp.audit.assistant;

/**
 * One LLM completion: the model's text reply plus token usage when the provider reports it
 * ({@code null} when unknown — e.g. a stubbed client). Returned by {@link LlmClient#complete} so the
 * assistant can record token usage in the AI trace (ADR-0014) without a second provider call.
 */
public record LlmResult(String text, Integer inputTokens, Integer outputTokens) {

	/** A result with just the text and no token counts (tests, or a provider that omits usage). */
	public static LlmResult text(String text) {
		return new LlmResult(text, null, null);
	}

}
