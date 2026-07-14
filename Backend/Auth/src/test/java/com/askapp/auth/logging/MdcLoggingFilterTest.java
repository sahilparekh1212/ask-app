package com.askapp.auth.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.slf4j.MDC;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MdcLoggingFilterTest {

	private final MdcLoggingFilter filter = new MdcLoggingFilter();

	/** A structurally-valid JWT (header.payload.signature) carrying the given payload JSON. */
	private static String jwt(String payloadJson) {
		String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(payloadJson.getBytes());
		return "header." + payload + ".signature";
	}

	/**
	 * Runs the filter and captures the MDC values that were visible to the chain (the
	 * filter clears MDC in a finally block, so they cannot be read after it returns).
	 */
	private Map<String, String> runAndCaptureMdc(HttpServletRequest request, HttpServletResponse response)
			throws Exception {
		FilterChain chain = mock(FilterChain.class);
		Map<String, String> captured = new HashMap<>();
		Answer<Void> capture = invocation -> {
			captured.put("requestId", MDC.get("requestId"));
			captured.put("userId", MDC.get("userId"));
			captured.put("urlParts", MDC.get("urlParts"));
			return null;
		};
		org.mockito.Mockito.doAnswer(capture).when(chain).doFilter(request, response);
		filter.doFilterInternal(request, response, chain);
		verify(chain).doFilter(request, response);
		return captured;
	}

	private HttpServletRequest request(String requestIdHeader, String authHeader, String uri) {
		HttpServletRequest request = mock(HttpServletRequest.class);
		when(request.getHeader(MdcLoggingFilter.REQUEST_ID_HEADER)).thenReturn(requestIdHeader);
		when(request.getHeader("Authorization")).thenReturn(authHeader);
		when(request.getRequestURI()).thenReturn(uri);
		return request;
	}

	@Test
	void honoursSuppliedRequestIdAndEchoesItBack() throws Exception {
		HttpServletRequest request = request("req-abc", null, "/api/auth/refresh");
		HttpServletResponse response = mock(HttpServletResponse.class);

		Map<String, String> mdc = runAndCaptureMdc(request, response);

		assertThat(mdc).containsEntry("requestId", "req-abc");
		verify(response).setHeader(MdcLoggingFilter.REQUEST_ID_HEADER, "req-abc");
		assertThat(MDC.get("requestId")).isNull();
	}

	@Test
	void generatesRequestIdWhenHeaderMissingOrBlank() throws Exception {
		HttpServletResponse response = mock(HttpServletResponse.class);

		Map<String, String> missing = runAndCaptureMdc(request(null, null, "/x/y"), mock(HttpServletResponse.class));
		Map<String, String> blank = runAndCaptureMdc(request("   ", null, "/x/y"), response);

		assertThat(missing.get("requestId")).isNotBlank();
		assertThat(blank.get("requestId")).isNotBlank();
		verify(response).setHeader(org.mockito.ArgumentMatchers.eq(MdcLoggingFilter.REQUEST_ID_HEADER),
			org.mockito.ArgumentMatchers.anyString());
	}

	@Test
	void extractsSubFromBearerToken() throws Exception {
		HttpServletRequest request = request(null, "Bearer " + jwt("{\"sub\":\"user-123\"}"), "/api/auth");

		Map<String, String> mdc = runAndCaptureMdc(request, mock(HttpServletResponse.class));

		assertThat(mdc).containsEntry("userId", "user-123");
	}

	@Test
	void fallsBackToAnonymousForMissingMalformedOrSubLessTokens() throws Exception {
		assertThat(runAndCaptureMdc(request(null, null, "/x/y"), mock(HttpServletResponse.class)))
			.containsEntry("userId", "anonymous");
		assertThat(runAndCaptureMdc(request(null, "Bearer header.payload", "/x/y"), mock(HttpServletResponse.class)))
			.containsEntry("userId", "anonymous");
		assertThat(runAndCaptureMdc(request(null, "Bearer a.!!!.c", "/x/y"), mock(HttpServletResponse.class)))
			.containsEntry("userId", "anonymous");
		assertThat(runAndCaptureMdc(request(null, "Bearer " + jwt("{\"x\":1}"), "/x/y"), mock(HttpServletResponse.class)))
			.containsEntry("userId", "anonymous");
	}

	@Test
	void summarisesUriToLastTwoSegments() throws Exception {
		assertThat(runAndCaptureMdc(request(null, null, "/api/auth/refresh"), mock(HttpServletResponse.class)))
			.containsEntry("urlParts", "auth/refresh");
		assertThat(runAndCaptureMdc(request(null, null, "/health"), mock(HttpServletResponse.class)))
			.containsEntry("urlParts", "health");
		assertThat(runAndCaptureMdc(request(null, null, "/"), mock(HttpServletResponse.class)))
			.containsEntry("urlParts", "/");
	}
}
