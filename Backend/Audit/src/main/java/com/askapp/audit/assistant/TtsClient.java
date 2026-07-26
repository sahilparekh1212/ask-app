package com.askapp.audit.assistant;

/**
 * Turns text into spoken audio for the assistant's read-aloud. Kept as an interface (like
 * {@link LlmClient}) so the provider is swappable and tests can inject a double without a
 * network call.
 */
public interface TtsClient {

	/** Synthesize {@code text} to MP3 audio bytes using the server-configured voice. */
	byte[] synthesizeMp3(String text);

}
