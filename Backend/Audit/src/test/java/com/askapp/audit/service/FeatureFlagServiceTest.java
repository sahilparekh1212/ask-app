package com.askapp.audit.service;

import com.askapp.audit.dto.FeatureFlagResponse;
import com.askapp.audit.model.FeatureFlag;
import com.askapp.audit.repository.FeatureFlagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FeatureFlagServiceTest {

	private FeatureFlagRepository repository;
	private FeatureFlagService service;

	@BeforeEach
	void setUp() {
		repository = mock(FeatureFlagRepository.class);
		service = new FeatureFlagService(repository);
	}

	@Test
	void list_mapsEntitiesToDtosPreservingOrder() {
		when(repository.findAllByOrderByFlagKeyAsc()).thenReturn(List.of(
			new FeatureFlag("chat", true, "LLM chat assistant."),
			new FeatureFlag("voice", false, "Voice chat.")));

		List<FeatureFlagResponse> result = service.list();

		assertThat(result).containsExactly(
			new FeatureFlagResponse("chat", true, "LLM chat assistant."),
			new FeatureFlagResponse("voice", false, "Voice chat."));
	}

	@Test
	void list_returnsEmptyWhenNoFlags() {
		when(repository.findAllByOrderByFlagKeyAsc()).thenReturn(List.of());

		assertThat(service.list()).isEmpty();
	}
}
