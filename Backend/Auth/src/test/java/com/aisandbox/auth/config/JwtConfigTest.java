package com.aisandbox.auth.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class JwtConfigTest {

	private final JwtConfig config = new JwtConfig();

	// PEM markers are assembled from fragments so secret scanners (gitleaks /
	// detect-private-key) don't false-positive on this test fixture — the key itself is
	// generated at runtime, nothing secret is committed. The runtime string is a normal PEM.
	private static final String BEGIN = "-----BEGIN PRIVATE" + " KEY-----";
	private static final String END = "-----END PRIVATE" + " KEY-----";

	private static String pkcs8Pem(KeyPair keyPair) {
		String body = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
		return BEGIN + "\n" + body + "\n" + END;
	}

	@Test
	void rsaKeyPair_generatesAnEphemeralKeyWhenNoPemSupplied() throws Exception {
		KeyPair keyPair = config.rsaKeyPair("");

		assertThat(keyPair.getPublic()).isInstanceOf(RSAPublicKey.class);
		assertThat(keyPair.getPrivate()).isNotNull();
	}

	@Test
	void rsaKeyPair_derivesThePublicKeyFromTheSuppliedPrivatePem() throws Exception {
		KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(2048);
		KeyPair original = generator.generateKeyPair();

		KeyPair loaded = config.rsaKeyPair(pkcs8Pem(original));

		// The modulus of the derived public key must match the original key pair.
		assertThat(((RSAPublicKey) loaded.getPublic()).getModulus())
			.isEqualTo(((RSAPublicKey) original.getPublic()).getModulus());
	}

	@Test
	void buildsJwkSetSourceEncoderAndDecoderBeans() throws Exception {
		KeyPair keyPair = config.rsaKeyPair("");

		JWKSet jwkSet = config.jwkSet(keyPair);
		assertThat(jwkSet.getKeys()).hasSize(1);

		JWKSource<SecurityContext> source = config.jwkSource(jwkSet);
		JwtEncoder encoder = config.jwtEncoder(source);
		JwtDecoder decoder = config.jwtDecoder(keyPair);

		assertThat(source).isNotNull();
		assertThat(encoder).isNotNull();
		assertThat(decoder).isNotNull();
	}
}
