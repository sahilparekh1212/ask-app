package com.askapp.audit.controller;

import com.askapp.audit.dto.FeatureFlagResponse;
import com.askapp.audit.service.FeatureFlagService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FeatureFlagControllerTest {

	private final FeatureFlagService service = mock(FeatureFlagService.class);

	@Test
	void flags_returnsWhateverTheServiceLists() {
		List<FeatureFlagResponse> flags = List.of(
			new FeatureFlagResponse("chat", true, "LLM chat assistant."),
			new FeatureFlagResponse("voice", true, "Voice chat."));
		when(service.list()).thenReturn(flags);

		assertThat(new FeatureFlagController(service).flags()).isEqualTo(flags);
	}

}
