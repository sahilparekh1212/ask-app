package com.askapp.audit.trace;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Configuration for AI interaction tracing (ADR-0014). {@code enabled} turns the whole feature on/off;
 * {@code captureContent} gates only the raw text (the query and reply) — the retrieval scores, ranks
 * and sources are public corpus metadata and are captured regardless, so quality analysis keeps
 * working with content capture off.
 */
@Component
public class AiTraceProperties {

	private final boolean enabled;
	private final boolean captureContent;

	public AiTraceProperties(
			@Value("${ai.trace.enabled:true}") boolean enabled,
			@Value("${ai.trace.capture-content:true}") boolean captureContent) {
		this.enabled = enabled;
		this.captureContent = captureContent;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public boolean isCaptureContent() {
		return captureContent;
	}

}
