package com.askapp.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

// scanBasePackages also covers com.askapp.common (the shared AuditEvent + ratelimit module),
// which is outside this package's tree and so wouldn't be picked up by default component scan.
@SpringBootApplication(scanBasePackages = {"com.askapp.auth", "com.askapp.common"})
@EnableAsync // backs AuditEventPublisher's fire-and-forget @Async publishing
public class AuthApplication {

	public static void main(String[] args) {
		SpringApplication.run(AuthApplication.class, args);
	}

}
