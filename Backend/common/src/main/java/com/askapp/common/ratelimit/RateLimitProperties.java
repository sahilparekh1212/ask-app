package com.askapp.common.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Tunable rate-limit settings (bound from {@code ratelimit.*}). Profiles can
 * disable the limiter or change the client retry hint per environment.
 */
@Component
@ConfigurationProperties(prefix = "ratelimit")
public class RateLimitProperties {

	/** Master switch — lets LOCAL/test turn the limiter off entirely. */
	private boolean enabled = true;

	/** Seconds advertised to a discarded client via the {@code Retry-After} header. */
	private int retryAfterSeconds = 30;

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public int getRetryAfterSeconds() {
		return retryAfterSeconds;
	}

	public void setRetryAfterSeconds(int retryAfterSeconds) {
		this.retryAfterSeconds = retryAfterSeconds;
	}

}
