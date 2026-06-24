package com.aisandbox.notification.audit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

/**
 * Emits one structured audit event per request (what / when / outcome), captured in the
 * application logs under the {@code AUDIT} logger, and forwards state-changing actions to
 * the standalone Audit service via {@link AuditPublisher}.
 *
 * <p>The actor (userId), timestamp and correlation id (requestId) come from the MDC that
 * {@code MdcLoggingFilter} populates. Only the HTTP method, request path, status and
 * duration are recorded — never request/response bodies, headers or query strings — so no
 * personally identifiable information is captured.
 */
@Component
public class AuditInterceptor implements HandlerInterceptor {

	private static final Logger audit = LoggerFactory.getLogger("AUDIT");
	private static final String START_ATTR = AuditInterceptor.class.getName() + ".start";
	private static final Set<String> MUTATIONS = Set.of("POST", "PUT", "PATCH", "DELETE");

	private final AuditPublisher auditPublisher;

	public AuditInterceptor(AuditPublisher auditPublisher) {
		this.auditPublisher = auditPublisher;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		request.setAttribute(START_ATTR, System.currentTimeMillis());
		return true;
	}

	@Override
	public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
			Exception ex) {
		Object start = request.getAttribute(START_ATTR);
		long durationMs = (start != null) ? System.currentTimeMillis() - (long) start : -1;
		String method = request.getMethod();
		String outcome = (ex == null) ? "SUCCESS" : "ERROR";
		audit.info("AUDIT action={} resource={} status={} outcome={} durationMs={}",
			method, request.getRequestURI(), response.getStatus(), outcome, durationMs);

		// Persist state-changing actions to the Audit service (non-PII fields only).
		if (MUTATIONS.contains(method)) {
			String details = "status=" + response.getStatus() + " outcome=" + outcome + " durationMs=" + durationMs;
			auditPublisher.publish(request.getRequestURI(), method, details,
				request.getHeader("Authorization"), MDC.get("requestId"));
		}
	}

}
