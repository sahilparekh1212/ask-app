package com.aisandbox.audit.config;

import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.util.Optional;

/**
 * Enables Spring Data JPA auditing. The "auditor" is the request-correlation UUID
 * (the UI's X-Request-Id, surfaced via MDC by the logging filter) — deliberately not a
 * user identity, so no personally identifiable information is persisted.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
public class JpaAuditingConfig {

	@Bean
	public AuditorAware<String> auditorProvider() {
		return () -> Optional.ofNullable(MDC.get("requestId"));
	}

}
