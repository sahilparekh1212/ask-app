package com.askapp.audit.rag.rerank;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RerankPropertiesTest {

	@Test
	void exposesConfiguredValues() {
		RerankProperties props = new RerankProperties(true, "rerank-2.5-lite", 20);
		assertThat(props.isEnabled()).isTrue();
		assertThat(props.getModel()).isEqualTo("rerank-2.5-lite");
		assertThat(props.getCandidatePoolSize()).isEqualTo(20);
	}

	@Test
	void disabledFlagIsHonoured() {
		assertThat(new RerankProperties(false, "m", 10).isEnabled()).isFalse();
	}

}
