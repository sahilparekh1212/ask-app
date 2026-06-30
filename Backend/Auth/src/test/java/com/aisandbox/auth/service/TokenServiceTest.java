package com.aisandbox.auth.service;

import com.aisandbox.auth.model.TokenResponse;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TokenServiceTest {

	private TokenService tokenService;
	private JwtDecoder jwtDecoder;

	@BeforeEach
	void setUp() throws Exception {
		KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
		gen.initialize(2048);
		KeyPair keyPair = gen.generateKeyPair();

		RSAKey rsaKey = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
			.privateKey((RSAPrivateKey) keyPair.getPrivate())
			.keyID(UUID.randomUUID().toString())
			.build();
		JWKSource<SecurityContext> jwkSource = new ImmutableJWKSet<>(new JWKSet(rsaKey));

		tokenService = new TokenService(new NimbusJwtEncoder(jwkSource));
		jwtDecoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) keyPair.getPublic()).build();
	}

	@Test
	void generateTokens_returnsAccessTokenWithExpectedClaims() {
		TokenResponse tokens = tokenService.generateTokens("user-1", "user1@example.com", "User One");

		Jwt decoded = jwtDecoder.decode(tokens.accessToken());

		assertThat(decoded.getSubject()).isEqualTo("user-1");
		assertThat(decoded.getClaimAsString("email")).isEqualTo("user1@example.com");
		assertThat(decoded.getClaimAsString("name")).isEqualTo("User One");
		assertThat(decoded.getClaimAsString("iss")).isEqualTo("aisandbox-auth");
		assertThat(tokens.expiresIn()).isEqualTo(1800L);
	}

	@Test
	void generateTokens_omitsEmailAndNameClaimsWhenNull() {
		TokenResponse tokens = tokenService.generateTokens("user-2", null, null);

		Jwt decoded = jwtDecoder.decode(tokens.accessToken());

		assertThat(decoded.getSubject()).isEqualTo("user-2");
		assertThat(decoded.getClaims()).doesNotContainKeys("email", "name");
	}

	@Test
	void generateTokens_accessTokenExpiresInThirtyMinutes() {
		TokenResponse tokens = tokenService.generateTokens("user-3", null, null);

		Jwt decoded = jwtDecoder.decode(tokens.accessToken());
		long secondsUntilExpiry = decoded.getExpiresAt().getEpochSecond() - decoded.getIssuedAt().getEpochSecond();

		assertThat(secondsUntilExpiry).isEqualTo(1800L);
	}

	@Test
	void consumeRefreshToken_returnsUserIdOnFirstUse() {
		TokenResponse tokens = tokenService.generateTokens("user-4", null, null);

		Optional<String> userId = tokenService.consumeRefreshToken(tokens.refreshToken());

		assertThat(userId).contains("user-4");
	}

	@Test
	void consumeRefreshToken_isSingleUse() {
		TokenResponse tokens = tokenService.generateTokens("user-5", null, null);

		tokenService.consumeRefreshToken(tokens.refreshToken());
		Optional<String> secondAttempt = tokenService.consumeRefreshToken(tokens.refreshToken());

		assertThat(secondAttempt).isEmpty();
	}

	@Test
	void consumeRefreshToken_unknownTokenReturnsEmpty() {
		Optional<String> userId = tokenService.consumeRefreshToken("not-a-real-token");

		assertThat(userId).isEmpty();
	}

	@Test
	void revokeRefreshToken_invalidatesFutureConsumption() {
		TokenResponse tokens = tokenService.generateTokens("user-6", null, null);

		tokenService.revokeRefreshToken(tokens.refreshToken());
		Optional<String> userId = tokenService.consumeRefreshToken(tokens.refreshToken());

		assertThat(userId).isEmpty();
	}

	@Test
	void revokeRefreshToken_unknownTokenIsNoOp() {
		tokenService.revokeRefreshToken("never-issued");
	}

	@Test
	void generateTokens_issuesDistinctRefreshTokensPerCall() {
		TokenResponse first = tokenService.generateTokens("user-7", null, null);
		TokenResponse second = tokenService.generateTokens("user-7", null, null);

		assertThat(first.refreshToken()).isNotEqualTo(second.refreshToken());
		assertThat(tokenService.consumeRefreshToken(first.refreshToken())).contains("user-7");
		assertThat(tokenService.consumeRefreshToken(second.refreshToken())).contains("user-7");
	}
}
