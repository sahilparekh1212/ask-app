package com.aisandbox.auth.handler;

import com.aisandbox.auth.event.AuditEventPublisher;
import com.aisandbox.auth.model.TokenResponse;
import com.aisandbox.auth.service.TokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OAuth2LoginSuccessHandlerTest {

	@Test
	void writesIssuedTokensAsJsonAndDerivesClaimsFromThePrincipal() throws Exception {
		TokenService tokenService = mock(TokenService.class);
		AuditEventPublisher auditEventPublisher = mock(AuditEventPublisher.class);
		OAuth2LoginSuccessHandler handler =
			new OAuth2LoginSuccessHandler(tokenService, new ObjectMapper(), auditEventPublisher);

		OAuth2User user = mock(OAuth2User.class);
		when(user.getAttribute("sub")).thenReturn("user-1");
		when(user.getAttribute("email")).thenReturn("eve@example.com");
		when(user.getAttribute("name")).thenReturn("Eve");
		Authentication authentication = mock(Authentication.class);
		when(authentication.getPrincipal()).thenReturn(user);

		when(tokenService.generateTokens("user-1", "eve@example.com", "Eve"))
			.thenReturn(new TokenResponse("access-xyz", "refresh-xyz", 900));

		HttpServletRequest request = mock(HttpServletRequest.class);
		HttpServletResponse response = mock(HttpServletResponse.class);
		StringWriter body = new StringWriter();
		when(response.getWriter()).thenReturn(new PrintWriter(body));

		handler.onAuthenticationSuccess(request, response, authentication);

		verify(tokenService).generateTokens("user-1", "eve@example.com", "Eve");
		verify(auditEventPublisher).publish("User", "LOGIN", "user-1");
		verify(response).setStatus(HttpServletResponse.SC_OK);
		verify(response).setContentType("application/json");
		assertThat(body.toString())
			.contains("access-xyz")
			.contains("refresh-xyz")
			.contains("900");
	}
}
