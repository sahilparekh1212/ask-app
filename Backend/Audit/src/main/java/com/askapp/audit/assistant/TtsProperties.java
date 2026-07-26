package com.askapp.audit.assistant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Configuration for read-aloud speech synthesis (server-side Google Cloud Text-to-Speech). Same
 * posture as {@link AssistantProperties}: the API key lives only in this service's environment
 * ({@code GOOGLE_TTS_API_KEY}) and is never exposed to the SPA, which talks exclusively to our own
 * {@code /api/v1/assistant/speak} proxy. When no key is configured the endpoint degrades to 503
 * (rather than failing startup) and the SPA falls back to the browser's built-in voice.
 *
 * <p>The default voice is a Chirp3-HD voice — Google's most natural, "Assistant-grade" tier — and
 * both the voice and the length cap are overridable without a redeploy of logic. The cap guards the
 * provider's ~5000-byte per-request limit; English prose is ~1 byte/char, so 4800 chars stays safe.
 */
@Component
public class TtsProperties {

	private final String apiKey;
	private final String voiceName;
	private final String languageCode;
	private final int maxChars;

	public TtsProperties(
			@Value("${tts.api-key:}") String apiKey,
			@Value("${tts.voice-name:en-US-Chirp3-HD-Kore}") String voiceName,
			@Value("${tts.language-code:en-US}") String languageCode,
			@Value("${tts.max-chars:4800}") int maxChars) {
		this.apiKey = apiKey;
		this.voiceName = voiceName;
		this.languageCode = languageCode;
		this.maxChars = maxChars;
	}

	public String getApiKey() {
		return apiKey;
	}

	public String getVoiceName() {
		return voiceName;
	}

	public String getLanguageCode() {
		return languageCode;
	}

	public int getMaxChars() {
		return maxChars;
	}

	/** True when an API key is present, i.e. the server can actually synthesize speech. */
	public boolean isConfigured() {
		return apiKey != null && !apiKey.isBlank();
	}

}
