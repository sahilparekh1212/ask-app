package com.aisandbox.auth.audit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditInterceptorTest {

	private final AuditInterceptor interceptor = new AuditInterceptor();

	@Test
	void preHandle_recordsStartTimeAndProceeds() {
		HttpServletRequest request = mock(HttpServletRequest.class);

		boolean proceed = interceptor.preHandle(request, mock(HttpServletResponse.class), new Object());

		assertThat(proceed).isTrue();
		verify(request).setAttribute(anyString(), any());
	}

	@Test
	void afterCompletion_logsSuccessfulOutcomeUsingTheRecordedStart() {
		HttpServletRequest request = mock(HttpServletRequest.class);
		HttpServletResponse response = mock(HttpServletResponse.class);
		when(request.getAttribute(anyString())).thenReturn(System.currentTimeMillis() - 5);
		when(request.getMethod()).thenReturn("GET");
		when(request.getRequestURI()).thenReturn("/api/auth");
		when(response.getStatus()).thenReturn(200);

		interceptor.afterCompletion(request, response, new Object(), null);
	}

	@Test
	void afterCompletion_handlesMissingStartAndErrorOutcome() {
		HttpServletRequest request = mock(HttpServletRequest.class);
		HttpServletResponse response = mock(HttpServletResponse.class);
		when(request.getAttribute(anyString())).thenReturn(null);
		when(request.getMethod()).thenReturn("POST");
		when(request.getRequestURI()).thenReturn("/api/auth");
		when(response.getStatus()).thenReturn(500);

		interceptor.afterCompletion(request, response, new Object(), new RuntimeException("boom"));
	}
}
