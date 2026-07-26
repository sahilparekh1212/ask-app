package com.askapp.audit.assistant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Read-aloud speech synthesis for assistant replies. Thin by design: it enforces the server-side-key
 * gate (503 when unconfigured, so the SPA falls back to the browser voice), caps the text to the
 * provider's per-request limit, and turns any provider failure into the same 503 so a TTS outage
 * never surfaces as a 500. The text is the assistant's own reply (already Markdown-flattened by the
 * SPA), so no screening is needed here — it isn't user input bound for an LLM.
 */
@Service
public class SpeechService {

	private static final Logger log = LoggerFactory.getLogger(SpeechService.class);

	private final TtsProperties properties;
	private final TtsClient ttsClient;

	public SpeechService(TtsProperties properties, TtsClient ttsClient) {
		this.properties = properties;
		this.ttsClient = ttsClient;
	}

	/** Synthesize {@code text} to MP3 audio, or throw {@link AssistantUnavailableException} (503). */
	public byte[] synthesize(String text) {
		if (!properties.isConfigured()) {
			throw new AssistantUnavailableException(
				"Text-to-speech is not configured on this server (missing GOOGLE_TTS_API_KEY)");
		}
		String prepared = capLength(text.strip(), properties.getMaxChars());
		try {
			byte[] audio = ttsClient.synthesizeMp3(prepared);
			log.info("Synthesized read-aloud audio chars={} bytes={}", prepared.length(), audio.length);
			return audio;
		} catch (RuntimeException e) {
			log.error("TTS provider call failed: {}", e.getClass().getSimpleName());
			throw new AssistantUnavailableException("Text-to-speech is temporarily unavailable", e);
		}
	}

	// Keep the request under the provider's per-request limit, cutting at the last word boundary
	// before the cap so a word isn't split mid-way (a rare case — replies are usually far shorter).
	private static String capLength(String text, int maxChars) {
		if (text.length() <= maxChars) {
			return text;
		}
		int lastSpace = text.lastIndexOf(' ', maxChars);
		return text.substring(0, lastSpace > 0 ? lastSpace : maxChars);
	}

}
