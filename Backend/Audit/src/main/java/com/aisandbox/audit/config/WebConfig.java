package com.aisandbox.audit.config;

import com.aisandbox.audit.audit.AuditInterceptor;
import com.aisandbox.audit.dto.TimelineInterval;
import com.aisandbox.common.ratelimit.RateLimitInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
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

	@Override
	public void addFormatters(FormatterRegistry registry) {
		// The timeline API documents interval=hour|day; default enum binding is
		// case-sensitive and would reject exactly the documented form.
		registry.addConverter(String.class, TimelineInterval.class, TimelineInterval::from);
	}

}
