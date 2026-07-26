package com.askapp.audit.assistant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Base64;

/**
 * {@link TtsClient} backed by the Google Cloud Text-to-Speech REST API — the same engine behind
 * Google Assistant. This is the read-aloud analog of {@link AnthropicLlmClient} /
 * {@code VoyageEmbeddingClient}: same server-side-key posture, same lazy client construction so the
 * app starts (and every other endpoint works) key-less, and nothing from any inbound HTTP request
 * reaches the provider — only the reply text handed to {@link #synthesizeMp3} is sent.
 *
 * <p>The key travels in the {@code X-Goog-Api-Key} header rather than a {@code ?key=} query
 * parameter, so it never lands in a URL, access log, or trace.
 */
@Component
public class GoogleTtsClient implements TtsClient {

	private static final String BASE_URL = "https://texttospeech.googleapis.com/v1";

	private final TtsProperties properties;
	private volatile RestClient client;

	// @Autowired disambiguates for Spring — the other constructor exists only so tests can inject a
	// pre-built (mock-server-backed) RestClient without a network call.
	@Autowired
	public GoogleTtsClient(TtsProperties properties) {
		this.properties = properties;
	}

	/** Test constructor: inject a pre-built RestClient. */
	GoogleTtsClient(TtsProperties properties, RestClient client) {
		this.properties = properties;
		this.client = client;
	}

	@Override
	public byte[] synthesizeMp3(String text) {
		SynthesizeResponse response = getClient().post()
			.uri("/text:synthesize")
			.body(new SynthesizeRequest(
				new SynthesisInput(text),
				new VoiceSelection(properties.getLanguageCode(), properties.getVoiceName()),
				new AudioConfig("MP3")))
			.retrieve()
			.body(SynthesizeResponse.class);
		if (response == null || response.audioContent() == null || response.audioContent().isBlank()) {
			throw new IllegalStateException("Google TTS response carried no audio content");
		}
		return Base64.getDecoder().decode(response.audioContent());
	}

	// Package-private lazy build, mirroring VoyageEmbeddingClient: constructing the client does no
	// I/O, and SpeechService never calls this when unconfigured.
	RestClient getClient() {
		RestClient existing = client;
		if (existing != null) {
			return existing;
		}
		synchronized (this) {
			if (client == null) {
				client = RestClient.builder()
					.baseUrl(BASE_URL)
					.defaultHeader("X-Goog-Api-Key", properties.getApiKey())
					.build();
			}
			return client;
		}
	}

	// Request/response shapes for texttospeech.googleapis.com v1 text:synthesize. Field names are
	// the API's camelCase JSON; the response's audioContent is base64-encoded MP3.
	record SynthesizeRequest(SynthesisInput input, VoiceSelection voice, AudioConfig audioConfig) {
	}

	record SynthesisInput(String text) {
	}

	record VoiceSelection(String languageCode, String name) {
	}

	record AudioConfig(String audioEncoding) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record SynthesizeResponse(String audioContent) {
	}

}
