package com.askapp.audit.audit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Emits one structured audit event per request (what / when / outcome), captured in the
 * application logs under the {@code AUDIT} logger.
 *
 * <p>The actor (userId), timestamp and correlation id (requestId) are supplied by the MDC
 * that {@code MdcLoggingFilter} populates. Only the HTTP method, request path, status and
 * duration are recorded — never request/response bodies, headers or query strings — so no
 * personally identifiable information is captured.
 */
@Component
public class AuditInterceptor implements HandlerInterceptor {

	private static final Logger audit = LoggerFactory.getLogger("AUDIT");
	private static final String START_ATTR = AuditInterceptor.class.getName() + ".start";

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
		audit.info("AUDIT action={} resource={} status={} outcome={} durationMs={}",
			request.getMethod(), request.getRequestURI(), response.getStatus(),
			(ex == null) ? "SUCCESS" : "ERROR", durationMs);
	}

}
