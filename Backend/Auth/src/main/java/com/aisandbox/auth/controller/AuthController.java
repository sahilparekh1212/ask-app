package com.aisandbox.auth.controller;

import com.aisandbox.auth.event.AuditEventPublisher;
import com.aisandbox.auth.model.LoginRequest;
import com.aisandbox.auth.model.RefreshRequest;
import com.aisandbox.auth.model.Roles;
import com.aisandbox.auth.model.TokenResponse;
import com.aisandbox.auth.service.TokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/auth")
@Tag(name = "Auth", description = "Token management endpoints")
public class AuthController {

	private static final Logger log = LoggerFactory.getLogger(AuthController.class);

	// Identity minted for the shared demo account (kept distinct from any real Google user).
	private static final String DEMO_USER_ID = "demo-user";
	private static final String DEMO_EMAIL = "demo@aisandbox.dev";
	private static final String DEMO_NAME = "Demo User";

	private final TokenService tokenService;
	private final AuditEventPublisher auditEventPublisher;
	private final boolean demoEnabled;
	private final String demoUsername;
	private final String demoPassword;

	public AuthController(TokenService tokenService, AuditEventPublisher auditEventPublisher,
			@Value("${auth.demo.enabled:true}") boolean demoEnabled,
			@Value("${auth.demo.username:demo}") String demoUsername,
			@Value("${auth.demo.password:demo}") String demoPassword) {
		this.tokenService = tokenService;
		this.auditEventPublisher = auditEventPublisher;
		this.demoEnabled = demoEnabled;
		this.demoUsername = demoUsername;
		this.demoPassword = demoPassword;
	}

	/**
	 * Demo username/password login for recruiters: with the defaults, username and password are
	 * both {@code demo}. On a match it issues the very same access/refresh JWTs as the Google
	 * OAuth flow, so the rest of the system is exercised identically. Disable in a real
	 * deployment with {@code auth.demo.enabled=false}.
	 */
	@PostMapping("/login")
	@Operation(summary = "Demo username/password login (recruiter test account) — issues the same JWT as Google sign-in. "
		+ "Optionally set \"role\":\"ROLE_ADMIN\" in the request body to test admin-gated endpoints.")
	public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
		if (!demoEnabled) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
		}
		boolean credentialsMatch = request != null
			&& demoUsername.equals(request.username())
			&& demoPassword.equals(request.password());
		if (!credentialsMatch) {
			log.warn("Demo login rejected for username={}", request == null ? null : request.username());
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		String role = request.role() != null && !request.role().isBlank() ? request.role() : Roles.ROLE_USER;
		TokenResponse tokens = tokenService.generateTokens(DEMO_USER_ID, DEMO_EMAIL, DEMO_NAME, role);
		auditEventPublisher.publish("User", "LOGIN", DEMO_USER_ID);
		log.info("Issued demo tokens for the recruiter test account with role={}", role);
		return ResponseEntity.ok(tokens);
	}

	@PostMapping("/refresh")
	@Operation(summary = "Exchange a refresh token for a new access token (30 min TTL)")
	public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
		return tokenService.consumeRefreshToken(request.refreshToken())
			.map(claims -> {
				TokenResponse tokens = tokenService.generateTokens(
					claims.userId(), claims.email(), claims.name(), claims.role());
				auditEventPublisher.publish("User", "TOKEN_REFRESH", claims.userId());
				log.info("Refreshed access token for userId={}", claims.userId());
				return ResponseEntity.ok(tokens);
			})
			.orElseGet(() -> {
				log.warn("Refresh attempt with invalid or expired token");
				return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
			});
	}

	// /auth/me is a header-versioning sample (vs Audit's URI versioning): the URL stays the
	// same and the X-API-Version request header selects the response shape. Absent or
	// "X-API-Version: 1" gets v1; "X-API-Version: 2" gets the enriched v2 body.

	@GetMapping("/me")
	@Operation(summary = "v1 (default): the authenticated user's claims from the JWT")
	public ResponseEntity<Map<String, Object>> me(JwtAuthenticationToken principal) {
		Map<String, Object> claims = principal.getToken().getClaims();
		return ResponseEntity.ok(Map.of(
			"userId", claims.getOrDefault("sub", ""),
			"email", claims.getOrDefault("email", ""),
			"name", claims.getOrDefault("name", "")
		));
	}

	@GetMapping(value = "/me", headers = "X-API-Version=2")
	@Operation(summary = "v2 (X-API-Version: 2): same fields plus the token's issued/expiry times")
	public ResponseEntity<Map<String, Object>> meV2(JwtAuthenticationToken principal) {
		Jwt token = principal.getToken();
		Map<String, Object> claims = token.getClaims();
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("userId", claims.getOrDefault("sub", ""));
		body.put("email", claims.getOrDefault("email", ""));
		body.put("name", claims.getOrDefault("name", ""));
		body.put("issuedAt", token.getIssuedAt());
		body.put("expiresAt", token.getExpiresAt());
		return ResponseEntity.ok(body);
	}

	@PostMapping("/logout")
	@Operation(summary = "Revoke a refresh token")
	public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
		tokenService.revokeRefreshToken(request.refreshToken());
		auditEventPublisher.publish("User", "LOGOUT", null);
		return ResponseEntity.noContent().build();
	}

}
