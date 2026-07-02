package com.aisandbox.common.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Enforces "one active request per user per endpoint, newest wins".
 *
 * <p>On {@code preHandle} the request registers itself; any older in-flight
 * request for the same key is discarded (cancelled + later rolled back). On
 * {@code afterCompletion} the request deregisters and the worker thread's
 * interrupt flag is cleared so it cannot leak into the next pooled request.
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

	private static final String ATTR = RateLimitInterceptor.class.getName() + ".activeRequest";

	private final ActiveRequestRegistry registry;
	private final RateLimitProperties properties;

	public RateLimitInterceptor(ActiveRequestRegistry registry, RateLimitProperties properties) {
		this.registry = registry;
		this.properties = properties;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		if (!properties.isEnabled()) {
			return true;
		}
		ActiveRequest mine = registry.register(buildKey(request));
		DiscardContext.set(mine);
		request.setAttribute(ATTR, mine);
		return true;
	}

	@Override
	public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
			Exception ex) {
		ActiveRequest mine = (ActiveRequest) request.getAttribute(ATTR);
		if (mine != null) {
			registry.complete(mine);
		}
		DiscardContext.clear();
		// Consume any pending interrupt left by a discard() so this pooled thread
		// starts its next request with a clean interrupt status.
		Thread.interrupted();
	}

	/** Key = userId + method + path, so the limit is per user AND per endpoint. */
	private String buildKey(HttpServletRequest request) {
		return userId() + "|" + request.getMethod() + "|" + request.getRequestURI();
	}

	private String userId() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth != null && auth.getName() != null) {
			return auth.getName();
		}
		return "anonymous";
	}

}
