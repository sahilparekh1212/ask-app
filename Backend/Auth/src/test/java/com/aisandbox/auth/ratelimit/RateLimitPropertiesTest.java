package com.aisandbox.auth.ratelimit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitPropertiesTest {

	@Test
	void defaultsAreEnabledWithThirtySecondRetryHint() {
		RateLimitProperties properties = new RateLimitProperties();

		assertThat(properties.isEnabled()).isTrue();
		assertThat(properties.getRetryAfterSeconds()).isEqualTo(30);
	}

	@Test
	void settersOverrideTheDefaults() {
		RateLimitProperties properties = new RateLimitProperties();

		properties.setEnabled(false);
		properties.setRetryAfterSeconds(15);

		assertThat(properties.isEnabled()).isFalse();
		assertThat(properties.getRetryAfterSeconds()).isEqualTo(15);
	}
}
