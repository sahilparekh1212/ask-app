package com.aisandbox.auth.service;

import com.aisandbox.auth.model.TokenResponse;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TokenService {

	private static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(30);
	private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(7);

	private final JwtEncoder jwtEncoder;
	// Placeholder in-memory store — replace with Redis or DB in production
	private final ConcurrentHashMap<String, RefreshTokenEntry> refreshTokenStore = new ConcurrentHashMap<>();

	public TokenService(JwtEncoder jwtEncoder) {
		this.jwtEncoder = jwtEncoder;
	}

	public TokenResponse generateTokens(String userId, String email, String name) {
		String accessToken = buildAccessToken(userId, email, name);
		String refreshToken = UUID.randomUUID().toString();
		refreshTokenStore.put(refreshToken, new RefreshTokenEntry(userId, Instant.now().plus(REFRESH_TOKEN_TTL)));
		return new TokenResponse(accessToken, refreshToken, ACCESS_TOKEN_TTL.getSeconds());
	}

	public Optional<String> consumeRefreshToken(String refreshToken) {
		RefreshTokenEntry entry = refreshTokenStore.remove(refreshToken);
		if (entry == null || entry.expiresAt().isBefore(Instant.now())) {
			return Optional.empty();
		}
		return Optional.of(entry.userId());
	}

	public void revokeRefreshToken(String refreshToken) {
		refreshTokenStore.remove(refreshToken);
	}

	private String buildAccessToken(String userId, String email, String name) {
		Instant now = Instant.now();
		JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
			.issuer("aisandbox-auth")
			.issuedAt(now)
			.expiresAt(now.plus(ACCESS_TOKEN_TTL))
			.subject(userId);
		if (email != null) claims.claim("email", email);
		if (name != null) claims.claim("name", name);
		return jwtEncoder.encode(JwtEncoderParameters.from(claims.build())).getTokenValue();
	}

	private record RefreshTokenEntry(String userId, Instant expiresAt) {}

}
