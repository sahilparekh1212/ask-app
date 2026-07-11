package com.aisandbox.audit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

// scanBasePackages also covers com.aisandbox.common (the shared AuditEvent + ratelimit module),
// which is outside this package's tree and so wouldn't be picked up by default component scan.
@SpringBootApplication(scanBasePackages = {"com.aisandbox.audit", "com.aisandbox.common"})
@EnableAsync // backs AuditEventPublisher's fire-and-forget @Async publishing of AI-feature events
public class AuditApplication {

	public static void main(String[] args) {
		SpringApplication.run(AuditApplication.class, args);
	}

}
