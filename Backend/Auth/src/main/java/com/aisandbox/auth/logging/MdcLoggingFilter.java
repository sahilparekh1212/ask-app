package com.aisandbox.auth.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@Order(1)
public class MdcLoggingFilter extends OncePerRequestFilter {

	/** Correlation UUID supplied by the UI; we honour it and generate one only if absent. */
	public static final String REQUEST_ID_HEADER = "X-Request-Id";

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
			FilterChain chain) throws ServletException, IOException {
		try {
			String requestId = resolveRequestId(request);
			MDC.put("requestId", requestId);
			// Echo the correlation id back so the UI/clients can stitch responses to logs.
			response.setHeader(REQUEST_ID_HEADER, requestId);
			MDC.put("userId", extractUserId(request));
			MDC.put("urlParts", extractLastTwoParts(request.getRequestURI()));
			chain.doFilter(request, response);
		} finally {
			MDC.clear();
		}
	}

	private String resolveRequestId(HttpServletRequest request) {
		String headerValue = request.getHeader(REQUEST_ID_HEADER);
		if (headerValue != null && !headerValue.isBlank()) {
			return headerValue;
		}
		return UUID.randomUUID().toString();
	}

	private String extractUserId(HttpServletRequest request) {
		String auth = request.getHeader("Authorization");
		if (auth != null && auth.startsWith("Bearer ")) {
			return extractSubFromJwt(auth.substring(7));
		}
		return "anonymous";
	}

	private String extractSubFromJwt(String token) {
		try {
			String[] parts = token.split("\\.");
			if (parts.length == 3) {
				byte[] decoded = Base64.getUrlDecoder().decode(parts[1]);
				String payload = new String(decoded);
				int idx = payload.indexOf("\"sub\":\"");
				if (idx >= 0) {
					int start = idx + 7;
					int end = payload.indexOf("\"", start);
					if (end > start) return payload.substring(start, end);
				}
			}
		} catch (Exception ignored) {}
		return "anonymous";
	}

	private String extractLastTwoParts(String uri) {
		List<String> parts = Arrays.stream(uri.split("/"))
			.filter(p -> !p.isEmpty())
			.collect(Collectors.toList());
		if (parts.size() >= 2) {
			return parts.get(parts.size() - 2) + "/" + parts.get(parts.size() - 1);
		} else if (parts.size() == 1) {
			return parts.get(0);
		}
		return "/";
	}

}
