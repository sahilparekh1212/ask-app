package com.askapp.audit.assistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * One prior turn of the conversation, replayed by the client on each request so the server
 * stays stateless. Roles are constrained to the two the Messages API accepts; content is
 * length-capped and screened by {@code PromptScreener} exactly like the new message.
 *
 * @param role    {@code user} or {@code assistant}
 * @param content the turn's text
 */
public record ChatTurn(
	@NotBlank @Pattern(regexp = "user|assistant") String role,
	@NotBlank @Size(max = 4000) String content
) {
}
