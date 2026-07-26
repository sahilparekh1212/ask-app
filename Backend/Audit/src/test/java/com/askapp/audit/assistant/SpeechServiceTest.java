package com.askapp.audit.assistant;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SpeechServiceTest {

	private final TtsClient ttsClient = mock(TtsClient.class);

	private SpeechService service(String apiKey, int maxChars) {
		return new SpeechService(
			new TtsProperties(apiKey, "en-US-Chirp3-HD-Kore", "en-US", maxChars), ttsClient);
	}

	@Test
	void synthesizesTrimmedTextWhenConfigured() {
		byte[] mp3 = {7, 7};
		when(ttsClient.synthesizeMp3(anyString())).thenReturn(mp3);

		byte[] audio = service("key", 4800).synthesize("  Hello  ");

		assertThat(audio).isEqualTo(mp3);
		ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
		verify(ttsClient).synthesizeMp3(sent.capture());
		assertThat(sent.getValue()).isEqualTo("Hello");
	}

	@Test
	void unconfiguredDegradesTo503WithoutCallingProvider() {
		assertThatThrownBy(() -> service("", 4800).synthesize("hi"))
			.isInstanceOf(AssistantUnavailableException.class)
			.hasMessageContaining("GOOGLE_TTS_API_KEY");
		verifyNoInteractions(ttsClient);
	}

	@Test
	void providerFailureBecomes503() {
		when(ttsClient.synthesizeMp3(anyString())).thenThrow(new RuntimeException("boom"));

		assertThatThrownBy(() -> service("key", 4800).synthesize("hi"))
			.isInstanceOf(AssistantUnavailableException.class)
			.hasMessageContaining("temporarily unavailable");
	}

	@Test
	void capsOverlongTextAtAWordBoundary() {
		when(ttsClient.synthesizeMp3(anyString())).thenReturn(new byte[]{1});

		service("key", 10).synthesize("one two three four");

		ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
		verify(ttsClient).synthesizeMp3(sent.capture());
		assertThat(sent.getValue()).isEqualTo("one two");
	}

}
