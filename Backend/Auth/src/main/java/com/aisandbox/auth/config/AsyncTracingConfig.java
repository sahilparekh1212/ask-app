package com.aisandbox.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;

/**
 * Carries the active Observation (trace context) across the {@code @Async} boundary. Boot applies
 * any {@link TaskDecorator} bean to the auto-configured {@code applicationTaskExecutor} that backs
 * {@code @Async}, so {@link com.aisandbox.auth.event.AuditEventPublisher}'s fire-and-forget publish
 * keeps the originating request's trace: without this, the Kafka {@code send} span starts a fresh
 * root trace and the login → Kafka → audit-persist path shows up as two disconnected traces in
 * Tempo (found by actually looking, during the observability proof run).
 */
@Configuration
public class AsyncTracingConfig {

	@Bean
	public TaskDecorator contextPropagatingTaskDecorator() {
		return new ContextPropagatingTaskDecorator();
	}
}
