package com.askapp.auth.service;

import com.askapp.auth.model.TokenResponse;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class TokenService {

	private static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(30);
	private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(7);

	private final JwtEncoder jwtEncoder;
	// In-memory (single replica) or Redis (shared across replicas), selected by
	// auth.refresh-token.store — see RefreshTokenStore / ADR-0007.
	private final RefreshTokenStore refreshTokenStore;

	public TokenService(JwtEncoder jwtEncoder, RefreshTokenStore refreshTokenStore) {
		this.jwtEncoder = jwtEncoder;
		this.refreshTokenStore = refreshTokenStore;
	}

	public TokenResponse generateTokens(String userId, String email, String name, String role) {
		String accessToken = buildAccessToken(userId, email, name, role);
		String refreshToken = UUID.randomUUID().toString();
		refreshTokenStore.store(refreshToken,
			new RefreshTokenEntry(userId, email, name, role, Instant.now().plus(REFRESH_TOKEN_TTL)));
		return new TokenResponse(accessToken, refreshToken, ACCESS_TOKEN_TTL.getSeconds());
	}

	/** Claims carried by a refresh token, restored onto the access token minted from it. */
	public record RefreshClaims(String userId, String email, String name, String role) {}

	public Optional<RefreshClaims> consumeRefreshToken(String refreshToken) {
		return refreshTokenStore.consume(refreshToken)
			.map(entry -> new RefreshClaims(entry.userId(), entry.email(), entry.name(), entry.role()));
	}

	public void revokeRefreshToken(String refreshToken) {
		refreshTokenStore.revoke(refreshToken);
	}

	private String buildAccessToken(String userId, String email, String name, String role) {
		Instant now = Instant.now();
		JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
			.issuer("askapp-auth")
			.issuedAt(now)
			.expiresAt(now.plus(ACCESS_TOKEN_TTL))
			.subject(userId);
		if (email != null) claims.claim("email", email);
		if (name != null) claims.claim("name", name);
		// A single-element array so resource servers can map it straight onto Spring Security's
		// collection-shaped authorities claim (see Audit's JwtAuthenticationConverter).
		claims.claim("roles", List.of(role));
		return jwtEncoder.encode(JwtEncoderParameters.from(claims.build())).getTokenValue();
	}

}
