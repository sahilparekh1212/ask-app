package com.aisandbox.audit.assistant.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Body of {@code POST /api/v1/assistant/flashcards}. The count is capped so a single request
 * can't ask the model for an unbounded deck.
 *
 * @param count how many cards to generate (1..20)
 */
public record FlashcardRequest(
	@Min(1) @Max(20) int count
) {
}
