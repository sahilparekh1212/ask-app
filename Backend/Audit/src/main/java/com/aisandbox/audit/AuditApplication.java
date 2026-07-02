package com.aisandbox.audit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// scanBasePackages also covers com.aisandbox.common (the shared AuditEvent + ratelimit module),
// which is outside this package's tree and so wouldn't be picked up by default component scan.
@SpringBootApplication(scanBasePackages = {"com.aisandbox.audit", "com.aisandbox.common"})
public class AuditApplication {

	public static void main(String[] args) {
		SpringApplication.run(AuditApplication.class, args);
	}

}
