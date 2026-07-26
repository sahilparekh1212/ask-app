package com.askapp.audit.assistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/v1/assistant/speak}. Carries the (already Markdown-flattened) text of an
 * assistant reply to read aloud. The generous size bound just stops a client stuffing an unbounded
 * body through the proxy — the service truncates to the provider's per-request limit before
 * synthesizing.
 *
 * @param text the plain prose to synthesize (required)
 */
public record SpeakRequest(@NotBlank @Size(max = 20_000) String text) {
}
