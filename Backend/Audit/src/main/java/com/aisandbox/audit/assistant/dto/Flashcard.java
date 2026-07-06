package com.aisandbox.audit.assistant.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One generated study flashcard.
 *
 * @param question the prompt side
 * @param answer   the reveal side
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Flashcard(String question, String answer) {
}
