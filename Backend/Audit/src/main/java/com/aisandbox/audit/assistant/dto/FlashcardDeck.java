package com.aisandbox.audit.assistant.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * A generated deck of flashcards. Shape mirrors the JSON the model is asked to return
 * ({@code {"cards": [{"question": "...", "answer": "..."}]}}); unknown keys are ignored so a
 * chatty model doesn't break deserialization.
 *
 * @param cards the flashcards, in the order generated
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FlashcardDeck(List<Flashcard> cards) {
}
