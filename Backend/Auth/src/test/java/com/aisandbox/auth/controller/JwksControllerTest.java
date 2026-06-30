package com.aisandbox.auth.controller;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JwksControllerTest {

	@Test
	void jwks_exposesPublicKeysOnlyNeverThePrivateMaterial() throws Exception {
		KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(2048);
		KeyPair keyPair = generator.generateKeyPair();
		RSAKey rsaKey = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
			.privateKey(keyPair.getPrivate())
			.keyID("kid-1")
			.build();
		JwksController controller = new JwksController(new JWKSet(rsaKey));

		Map<String, Object> jwks = controller.jwks();

		assertThat(jwks).containsKey("keys");
		// "d" is the RSA private exponent — it must never appear in the public JWK set.
		assertThat(jwks.toString()).doesNotContain("\"d\"");
	}
}
