package com.askapp.common.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RateLimitInterceptorTest {

	private final ActiveRequestRegistry registry = mock(ActiveRequestRegistry.class);
	private final RateLimitProperties properties = new RateLimitProperties();
	private final RateLimitInterceptor interceptor = new RateLimitInterceptor(registry, properties);

	@AfterEach
	void cleanUp() {
		DiscardContext.clear();
		SecurityContextHolder.clearContext();
		Thread.interrupted();
	}

	@Test
	void preHandle_isANoOpWhenDisabled() {
		properties.setEnabled(false);

		boolean proceed = interceptor.preHandle(mock(HttpServletRequest.class), mock(HttpServletResponse.class),
			new Object());

		assertThat(proceed).isTrue();
		verifyNoInteractions(registry);
		assertThat(DiscardContext.current()).isNull();
	}

	@Test
	void preHandle_registersUnderAnAuthenticatedPerUserPerEndpointKey() {
		SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("alice", "pw"));
		HttpServletRequest request = mock(HttpServletRequest.class);
		when(request.getMethod()).thenReturn("GET");
		when(request.getRequestURI()).thenReturn("/api/resource");
		ActiveRequest mine = new ActiveRequest("alice|GET|/api/resource", Thread.currentThread());
		when(registry.register(anyString())).thenReturn(mine);

		boolean proceed = interceptor.preHandle(request, mock(HttpServletResponse.class), new Object());

		assertThat(proceed).isTrue();
		ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
		verify(registry).register(key.capture());
		assertThat(key.getValue()).isEqualTo("alice|GET|/api/resource");
		assertThat(DiscardContext.current()).isSameAs(mine);
		verify(request).setAttribute(anyString(), eq(mine));
	}

	@Test
	void preHandle_fallsBackToAnonymousWhenUnauthenticated() {
		HttpServletRequest request = mock(HttpServletRequest.class);
		when(request.getMethod()).thenReturn("POST");
		when(request.getRequestURI()).thenReturn("/api/resource");
		when(registry.register(anyString())).thenReturn(new ActiveRequest("k", Thread.currentThread()));

		interceptor.preHandle(request, mock(HttpServletResponse.class), new Object());

		ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
		verify(registry).register(key.capture());
		assertThat(key.getValue()).isEqualTo("anonymous|POST|/api/resource");
	}

	@Test
	void afterCompletion_completesTheRequestAndClearsContext() {
		HttpServletRequest request = mock(HttpServletRequest.class);
		ActiveRequest mine = new ActiveRequest("k", Thread.currentThread());
		when(request.getAttribute(anyString())).thenReturn(mine);
		DiscardContext.set(mine);

		interceptor.afterCompletion(request, mock(HttpServletResponse.class), new Object(), null);

		verify(registry).complete(mine);
		assertThat(DiscardContext.current()).isNull();
	}

	@Test
	void afterCompletion_withoutARegisteredRequestDoesNotComplete() {
		HttpServletRequest request = mock(HttpServletRequest.class);
		when(request.getAttribute(anyString())).thenReturn(null);

		interceptor.afterCompletion(request, mock(HttpServletResponse.class), new Object(), null);

		verify(registry, never()).complete(org.mockito.ArgumentMatchers.any());
	}
}
