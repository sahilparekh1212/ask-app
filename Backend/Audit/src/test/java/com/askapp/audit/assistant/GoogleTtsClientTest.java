package com.askapp.audit.assistant;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GoogleTtsClientTest {

	private static final String URL = "https://texttospeech.googleapis.com/v1/text:synthesize";

	private final TtsProperties properties =
		new TtsProperties("test-key", "en-US-Chirp3-HD-Kore", "en-US", 4800);

	private final RestClient.Builder builder =
		RestClient.builder().baseUrl("https://texttospeech.googleapis.com/v1");
	private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
	private final GoogleTtsClient client = new GoogleTtsClient(properties, builder.build());

	@Test
	void synthesizesTextWithConfiguredVoiceAndReturnsDecodedMp3() {
		byte[] mp3 = {10, 20, 30, 40};
		String audioContent = Base64.getEncoder().encodeToString(mp3);
		server.expect(requestTo(URL))
			.andExpect(method(HttpMethod.POST))
			.andExpect(jsonPath("$.input.text").value("Hello there"))
			.andExpect(jsonPath("$.voice.languageCode").value("en-US"))
			.andExpect(jsonPath("$.voice.name").value("en-US-Chirp3-HD-Kore"))
			.andExpect(jsonPath("$.audioConfig.audioEncoding").value("MP3"))
			.andRespond(withSuccess("{\"audioContent\":\"" + audioContent + "\"}",
				MediaType.APPLICATION_JSON));

		byte[] audio = client.synthesizeMp3("Hello there");

		assertThat(audio).isEqualTo(mp3);
		server.verify();
	}

	@Test
	void emptyAudioContentIsAnError() {
		server.expect(requestTo(URL))
			.andRespond(withSuccess("{\"audioContent\":\"\"}", MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> client.synthesizeMp3("hi"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("no audio content");
	}

	@Test
	void lazyClientBuildIsMemoizedAndPerformsNoIo() {
		GoogleTtsClient lazy = new GoogleTtsClient(properties);

		RestClient first = lazy.getClient();

		assertThat(first).isNotNull().isSameAs(lazy.getClient());
	}

}
