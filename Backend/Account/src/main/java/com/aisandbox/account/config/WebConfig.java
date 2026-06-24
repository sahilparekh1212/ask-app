package com.aisandbox.account.config;

import com.aisandbox.account.audit.AuditInterceptor;
import com.aisandbox.account.ratelimit.RateLimitInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Registers the rate-limiting and audit interceptors for all MVC endpoints. */
@Configuration
public class WebConfig implements WebMvcConfigurer {

	private final RateLimitInterceptor rateLimitInterceptor;
	private final AuditInterceptor auditInterceptor;

	public WebConfig(RateLimitInterceptor rateLimitInterceptor, AuditInterceptor auditInterceptor) {
		this.rateLimitInterceptor = rateLimitInterceptor;
		this.auditInterceptor = auditInterceptor;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(rateLimitInterceptor);
		registry.addInterceptor(auditInterceptor);
	}

}
