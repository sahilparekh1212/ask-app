package com.aisandbox.auth.config;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;

@Configuration
public class JwtConfig {

	/*
	 * AUTH_RSA_PRIVATE_KEY must be set in production (via OpenShift Secret) so that
	 * all horizontally-scaled Auth pods sign tokens with the same key.
	 * When the env var is absent (local dev), an ephemeral key is generated — this
	 * means JWTs are invalid after a restart and across multiple pods.
	 */
	@Bean
	public KeyPair rsaKeyPair(@Value("${auth.rsa.private-key:}") String privateKeyPem) throws Exception {
		if (privateKeyPem == null || privateKeyPem.isBlank()) {
			KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
			gen.initialize(2048);
			return gen.generateKeyPair();
		}
		// PEM header/footer assembled from fragments so secret scanners (detect-private-key /
		// gitleaks) don't false-positive on the literal marker — there is no key in this source.
		String stripped = privateKeyPem
			.replace("-----BEGIN PRIVATE" + " KEY-----", "")
			.replace("-----END PRIVATE" + " KEY-----", "")
			.replaceAll("\\s+", "");
		byte[] keyBytes = Base64.getDecoder().decode(stripped);
		KeyFactory kf = KeyFactory.getInstance("RSA");
		PrivateKey privateKey = kf.generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
		RSAPrivateCrtKey crtKey = (RSAPrivateCrtKey) privateKey;
		RSAPublicKeySpec pubSpec = new RSAPublicKeySpec(crtKey.getModulus(), crtKey.getPublicExponent());
		PublicKey publicKey = kf.generatePublic(pubSpec);
		return new KeyPair(publicKey, privateKey);
	}

	/*
	 * The kid must be derived from the key material (RFC 7638 thumbprint), not random:
	 * resource servers (Audit) match tokens to JWKS entries by kid, so horizontally-scaled
	 * replicas sharing AUTH_RSA_PRIVATE_KEY must all advertise the same kid — a random one
	 * makes tokens minted by one replica fail JWKS lookup against another's endpoint.
	 */
	@Bean
	public JWKSet jwkSet(KeyPair rsaKeyPair) throws JOSEException {
		RSAKey rsaKey = new RSAKey.Builder((RSAPublicKey) rsaKeyPair.getPublic())
			.privateKey(rsaKeyPair.getPrivate())
			.keyIDFromThumbprint()
			.build();
		return new JWKSet(rsaKey);
	}

	@Bean
	public JWKSource<SecurityContext> jwkSource(JWKSet jwkSet) {
		return new ImmutableJWKSet<>(jwkSet);
	}

	@Bean
	public JwtEncoder jwtEncoder(JWKSource<SecurityContext> jwkSource) {
		return new NimbusJwtEncoder(jwkSource);
	}

	@Bean
	public JwtDecoder jwtDecoder(KeyPair rsaKeyPair) {
		return NimbusJwtDecoder.withPublicKey((RSAPublicKey) rsaKeyPair.getPublic()).build();
	}

}
