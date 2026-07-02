package com.aisandbox.auth.handler;

import com.aisandbox.auth.event.AuditEventPublisher;
import com.aisandbox.auth.model.Roles;
import com.aisandbox.auth.model.TokenResponse;
import com.aisandbox.auth.service.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OAuth2LoginSuccessHandlerTest {

	private final TokenService tokenService = mock(TokenService.class);
	private final AuditEventPublisher auditEventPublisher = mock(AuditEventPublisher.class);
	private final HttpServletResponse response = mock(HttpServletResponse.class);
	private final OAuth2LoginSuccessHandler handler =
		new OAuth2LoginSuccessHandler(tokenService, auditEventPublisher, "http://localhost:4200");

	private static OAuth2User principal(String sub, String email, String name) {
		OAuth2User user = mock(OAuth2User.class);
		when(user.getAttribute("sub")).thenReturn(sub);
		when(user.getAttribute("email")).thenReturn(email);
		when(user.getAttribute("name")).thenReturn(name);
		return user;
	}

	private static Authentication authenticationFor(OAuth2User user) {
		Authentication authentication = mock(Authentication.class);
		when(authentication.getPrincipal()).thenReturn(user);
		return authentication;
	}

	private String capturedRedirectLocation() throws Exception {
		ArgumentCaptor<String> location = ArgumentCaptor.forClass(String.class);
		verify(response).sendRedirect(location.capture());
		return location.getValue();
	}

	@Test
	void redirectsToTheSpaCallbackWithTokensInTheUrlFragment() throws Exception {
		when(tokenService.generateTokens("user-1", "eve@example.com", "Eve", Roles.ROLE_USER))
			.thenReturn(new TokenResponse("access-xyz", "refresh-xyz", 900));

		handler.onAuthenticationSuccess(mock(HttpServletRequest.class), response,
			authenticationFor(principal("user-1", "eve@example.com", "Eve")));

		verify(tokenService).generateTokens("user-1", "eve@example.com", "Eve", Roles.ROLE_USER);
		verify(auditEventPublisher).publish("User", "LOGIN", "user-1");
		assertThat(capturedRedirectLocation())
			.startsWith("http://localhost:4200/login/callback#")
			.contains("access_token=access-xyz")
			.contains("refresh_token=refresh-xyz")
			.contains("expires_in=900")
			.contains("token_type=Bearer");
	}

	@Test
	void urlEncodesTokenValuesInTheFragmentSoReservedCharactersDontBreakParsing() throws Exception {
		when(tokenService.generateTokens("user-2", null, null, Roles.ROLE_USER))
			.thenReturn(new TokenResponse("a+b/c=", "r-e_f", 900));

		handler.onAuthenticationSuccess(mock(HttpServletRequest.class), response,
			authenticationFor(principal("user-2", null, null)));

		assertThat(capturedRedirectLocation())
			.doesNotContain("a+b/c=") // the raw, unencoded value would corrupt the fragment's key=value parsing
			.contains("access_token=a%2Bb%2Fc%3D")
			.contains("refresh_token=r-e_f"); // unreserved characters pass through URLEncoder unchanged
	}
}
