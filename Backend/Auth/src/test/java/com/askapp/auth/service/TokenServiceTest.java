package com.askapp.auth.service;

import com.askapp.auth.model.Roles;
import com.askapp.auth.model.TokenResponse;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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

		tokenService = new TokenService(new NimbusJwtEncoder(jwkSource), new InMemoryRefreshTokenStore());
		jwtDecoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) keyPair.getPublic()).build();
	}

	@Test
	void generateTokens_returnsAccessTokenWithExpectedClaims() {
		TokenResponse tokens = tokenService.generateTokens("user-1", "user1@example.com", "User One", Roles.ROLE_USER);

		Jwt decoded = jwtDecoder.decode(tokens.accessToken());

		assertThat(decoded.getSubject()).isEqualTo("user-1");
		assertThat(decoded.getClaimAsString("email")).isEqualTo("user1@example.com");
		assertThat(decoded.getClaimAsString("name")).isEqualTo("User One");
		assertThat(decoded.getClaimAsString("iss")).isEqualTo("askapp-auth");
		assertThat(tokens.expiresIn()).isEqualTo(1800L);
	}

	@Test
	void generateTokens_omitsEmailAndNameClaimsWhenNull() {
		TokenResponse tokens = tokenService.generateTokens("user-2", null, null, Roles.ROLE_USER);

		Jwt decoded = jwtDecoder.decode(tokens.accessToken());

		assertThat(decoded.getSubject()).isEqualTo("user-2");
		assertThat(decoded.getClaims()).doesNotContainKeys("email", "name");
	}

	@Test
	void generateTokens_putsTheRoleInACollectionShapedRolesClaim() {
		TokenResponse tokens = tokenService.generateTokens("user-admin", null, null, Roles.ROLE_ADMIN);

		Jwt decoded = jwtDecoder.decode(tokens.accessToken());

		assertThat(decoded.getClaimAsStringList("roles")).containsExactly(Roles.ROLE_ADMIN);
	}

	@Test
	void generateTokens_accessTokenExpiresInThirtyMinutes() {
		TokenResponse tokens = tokenService.generateTokens("user-3", null, null, Roles.ROLE_USER);

		Jwt decoded = jwtDecoder.decode(tokens.accessToken());
		long secondsUntilExpiry = decoded.getExpiresAt().getEpochSecond() - decoded.getIssuedAt().getEpochSecond();

		assertThat(secondsUntilExpiry).isEqualTo(1800L);
	}

	@Test
	void consumeRefreshToken_returnsUserIdOnFirstUse() {
		TokenResponse tokens = tokenService.generateTokens("user-4", null, null, Roles.ROLE_USER);

		Optional<TokenService.RefreshClaims> claims = tokenService.consumeRefreshToken(tokens.refreshToken());

		assertThat(claims).isPresent();
		assertThat(claims.get().userId()).isEqualTo("user-4");
	}

	@Test
	void consumeRefreshToken_preservesEmailNameAndRoleForTheNextAccessToken() {
		TokenResponse tokens = tokenService.generateTokens(
			"user-4b", "user4b@example.com", "User Four B", Roles.ROLE_ADMIN);

		Optional<TokenService.RefreshClaims> claims = tokenService.consumeRefreshToken(tokens.refreshToken());

		assertThat(claims).isPresent();
		assertThat(claims.get().email()).isEqualTo("user4b@example.com");
		assertThat(claims.get().name()).isEqualTo("User Four B");
		assertThat(claims.get().role()).isEqualTo(Roles.ROLE_ADMIN);
	}

	/**
	 * {@code ConcurrentHashMap.remove(key)} is atomic per key, so a naive read-then-remove race
	 * isn't actually possible here — this proves it under real concurrent load rather than
	 * assuming it, since "single-use under a race" is exactly the kind of claim that looks right
	 * in code review and is wrong in practice.
	 */
	@Test
	void consumeRefreshToken_isSingleUseUnderConcurrentRacingConsumers() throws Exception {
		TokenResponse tokens = tokenService.generateTokens("user-race", null, null, Roles.ROLE_USER);
		int racers = 50;
		ExecutorService pool = Executors.newFixedThreadPool(racers);
		try {
			List<Callable<Boolean>> attempts = IntStream.range(0, racers)
				.<Callable<Boolean>>mapToObj(i -> () -> tokenService.consumeRefreshToken(tokens.refreshToken()).isPresent())
				.collect(Collectors.toList());
			List<Future<Boolean>> results = pool.invokeAll(attempts);
			long successes = results.stream().filter(f -> {
				try {
					return f.get();
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}).count();

			assertThat(successes).isEqualTo(1);
		} finally {
			pool.shutdown();
		}
	}

	@Test
	void consumeRefreshToken_isSingleUse() {
		TokenResponse tokens = tokenService.generateTokens("user-5", null, null, Roles.ROLE_USER);

		tokenService.consumeRefreshToken(tokens.refreshToken());
		Optional<TokenService.RefreshClaims> secondAttempt = tokenService.consumeRefreshToken(tokens.refreshToken());

		assertThat(secondAttempt).isEmpty();
	}

	@Test
	void consumeRefreshToken_unknownTokenReturnsEmpty() {
		Optional<TokenService.RefreshClaims> claims = tokenService.consumeRefreshToken("not-a-real-token");

		assertThat(claims).isEmpty();
	}

	@Test
	void revokeRefreshToken_invalidatesFutureConsumption() {
		TokenResponse tokens = tokenService.generateTokens("user-6", null, null, Roles.ROLE_USER);

		tokenService.revokeRefreshToken(tokens.refreshToken());
		Optional<TokenService.RefreshClaims> claims = tokenService.consumeRefreshToken(tokens.refreshToken());

		assertThat(claims).isEmpty();
	}

	@Test
	void revokeRefreshToken_unknownTokenIsNoOp() {
		tokenService.revokeRefreshToken("never-issued");
	}

	@Test
	void generateTokens_issuesDistinctRefreshTokensPerCall() {
		TokenResponse first = tokenService.generateTokens("user-7", null, null, Roles.ROLE_USER);
		TokenResponse second = tokenService.generateTokens("user-7", null, null, Roles.ROLE_USER);

		assertThat(first.refreshToken()).isNotEqualTo(second.refreshToken());
		assertThat(tokenService.consumeRefreshToken(first.refreshToken()).map(TokenService.RefreshClaims::userId))
			.contains("user-7");
		assertThat(tokenService.consumeRefreshToken(second.refreshToken()).map(TokenService.RefreshClaims::userId))
			.contains("user-7");
	}
}
