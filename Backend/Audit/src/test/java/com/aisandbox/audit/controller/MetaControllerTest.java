package com.aisandbox.audit.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MetaControllerTest {

	@SuppressWarnings("unchecked")
	private final ObjectProvider<DemoDataController> demoProvider = mock(ObjectProvider.class);

	@Test
	void demoDataTrueWhenTheDemoControllerBeanExists() {
		// LOCAL/DEV: the @Profile-gated demo controller is a bean, so the flag is on.
		when(demoProvider.getIfAvailable()).thenReturn(mock(DemoDataController.class));

		assertThat(new MetaController(demoProvider).features().demoData()).isTrue();
	}

	@Test
	void demoDataFalseWhenTheDemoControllerBeanIsAbsent() {
		// SIT/UAT/PROD: no demo controller bean → the SPA hides the "Add demo logs" button.
		when(demoProvider.getIfAvailable()).thenReturn(null);

		assertThat(new MetaController(demoProvider).features().demoData()).isFalse();
	}

}
