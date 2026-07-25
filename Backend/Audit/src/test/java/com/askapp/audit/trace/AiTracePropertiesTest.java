package com.askapp.audit.trace;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiTracePropertiesTest {

	@Test
	void exposesConfiguredFlags() {
		AiTraceProperties props = new AiTraceProperties(true, false);
		assertThat(props.isEnabled()).isTrue();
		assertThat(props.isCaptureContent()).isFalse();
	}

	@Test
	void disabledFlagIsHonoured() {
		assertThat(new AiTraceProperties(false, true).isEnabled()).isFalse();
		assertThat(new AiTraceProperties(true, true).isCaptureContent()).isTrue();
	}

}
