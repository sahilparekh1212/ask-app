package com.aisandbox.auth.controller;

import com.aisandbox.auth.event.AuditEventPublisher;
import com.aisandbox.auth.model.LoginRequest;
import com.aisandbox.auth.model.RefreshRequest;
import com.aisandbox.auth.model.Roles;
import com.aisandbox.auth.model.TokenResponse;
import com.aisandbox.auth.service.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthControllerTest {

	private TokenService tokenService;
	private AuditEventPublisher auditEventPublisher;
	private AuthController controller;

	@BeforeEach
	void setUp() {
		tokenService = mock(TokenService.class);
		auditEventPublisher = mock(AuditEventPublisher.class);
		controller = new AuthController(tokenService, auditEventPublisher, true, "demo", "demo");
	}

	@Test
	void login_issuesTokensAndPublishesALoginEvent() {
		TokenResponse tokens = new TokenResponse("access", "refresh", 1800L);
		when(tokenService.generateTokens("demo-user", "demo@aisandbox.dev", "Demo User", Roles.ROLE_USER))
			.thenReturn(tokens);

		ResponseEntity<TokenResponse> response = controller.login(new LoginRequest("demo", "demo", null));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isEqualTo(tokens);
		verify(auditEventPublisher).publish("User", "LOGIN", "demo-user");
	}

	@Test
	void login_mintsTheRequestedRoleWhenGiven() {
		TokenResponse tokens = new TokenResponse("access", "refresh", 1800L);
		when(tokenService.generateTokens("demo-user", "demo@aisandbox.dev", "Demo User", Roles.ROLE_ADMIN))
			.thenReturn(tokens);

		ResponseEntity<TokenResponse> response = controller.login(new LoginRequest("demo", "demo", Roles.ROLE_ADMIN));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		verify(tokenService).generateTokens("demo-user", "demo@aisandbox.dev", "Demo User", Roles.ROLE_ADMIN);
	}

	@Test
	void login_returns401ForWrongCredentials() {
		ResponseEntity<TokenResponse> response = controller.login(new LoginRequest("demo", "wrong", null));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(response.getBody()).isNull();
		verify(tokenService, never()).generateTokens(any(), any(), any(), any());
	}

	@Test
	void login_returns404WhenDemoLoginIsDisabled() {
		AuthController disabled = new AuthController(tokenService, auditEventPublisher, false, "demo", "demo");

		ResponseEntity<TokenResponse> response = disabled.login(new LoginRequest("demo", "demo", null));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		verify(tokenService, never()).generateTokens(any(), any(), any(), any());
	}

	@Test
	void refresh_returnsNewTokensWhenRefreshTokenIsValid() {
		TokenResponse newTokens = new TokenResponse("new-access-token", "new-refresh-token", 1800L);
		TokenService.RefreshClaims claims = new TokenService.RefreshClaims(
			"user-1", "user1@example.com", "User One", Roles.ROLE_ADMIN);
		when(tokenService.consumeRefreshToken("valid-refresh-token")).thenReturn(Optional.of(claims));
		when(tokenService.generateTokens("user-1", "user1@example.com", "User One", Roles.ROLE_ADMIN))
			.thenReturn(newTokens);

		ResponseEntity<TokenResponse> response = controller.refresh(new RefreshRequest("valid-refresh-token"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isEqualTo(newTokens);
		verify(auditEventPublisher).publish("User", "TOKEN_REFRESH", "user-1");
	}

	@Test
	void refresh_returns401WhenRefreshTokenIsInvalid() {
		when(tokenService.consumeRefreshToken("bad-token")).thenReturn(Optional.empty());

		ResponseEntity<TokenResponse> response = controller.refresh(new RefreshRequest("bad-token"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(response.getBody()).isNull();
		verify(tokenService, never()).generateTokens(any(), any(), any(), any());
	}

	@Test
	void logout_revokesTheGivenRefreshToken() {
		ResponseEntity<Void> response = controller.logout(new RefreshRequest("some-refresh-token"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
		verify(tokenService).revokeRefreshToken("some-refresh-token");
		verify(auditEventPublisher).publish("User", "LOGOUT", null);
	}

	@Test
	void me_returnsClaimsFromTheJwtPrincipal() {
		Jwt jwt = Jwt.withTokenValue("token-value")
			.header("alg", "RS256")
			.claim("sub", "user-1")
			.claim("email", "user1@example.com")
			.claim("name", "User One")
			.issuedAt(Instant.now())
			.expiresAt(Instant.now().plusSeconds(1800))
			.build();
		JwtAuthenticationToken principal = new JwtAuthenticationToken(jwt);

		ResponseEntity<Map<String, Object>> response = controller.me(principal);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody())
			.containsEntry("userId", "user-1")
			.containsEntry("email", "user1@example.com")
			.containsEntry("name", "User One");
	}

	@Test
	void meV2_addsTokenIssuedAndExpiryTimesToTheClaims() {
		Instant issuedAt = Instant.now();
		Instant expiresAt = issuedAt.plusSeconds(1800);
		Jwt jwt = Jwt.withTokenValue("token-value")
			.header("alg", "RS256")
			.claim("sub", "user-1")
			.claim("email", "user1@example.com")
			.claim("name", "User One")
			.issuedAt(issuedAt)
			.expiresAt(expiresAt)
			.build();

		ResponseEntity<Map<String, Object>> response = controller.meV2(new JwtAuthenticationToken(jwt));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody())
			.containsEntry("userId", "user-1")
			.containsEntry("email", "user1@example.com")
			.containsEntry("name", "User One")
			.containsEntry("issuedAt", issuedAt)
			.containsEntry("expiresAt", expiresAt);
	}

	@Test
	void me_defaultsMissingClaimsToEmptyString() {
		Jwt jwt = Jwt.withTokenValue("token-value")
			.header("alg", "RS256")
			.claim("sub", "user-2")
			.issuedAt(Instant.now())
			.expiresAt(Instant.now().plusSeconds(1800))
			.build();
		JwtAuthenticationToken principal = new JwtAuthenticationToken(jwt);

		ResponseEntity<Map<String, Object>> response = controller.me(principal);

		assertThat(response.getBody())
			.containsEntry("email", "")
			.containsEntry("name", "");
	}
}
