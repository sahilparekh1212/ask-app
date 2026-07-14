package com.askapp.auth.controller;

import com.nimbusds.jose.jwk.JWKSet;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@Tag(name = "JWKS", description = "Public key endpoint for token validation")
public class JwksController {

	private final JWKSet jwkSet;

	public JwksController(JWKSet jwkSet) {
		this.jwkSet = jwkSet;
	}

	@GetMapping("/.well-known/jwks.json")
	@Operation(summary = "Return the public JWK set used to validate access tokens")
	public Map<String, Object> jwks() {
		return jwkSet.toPublicJWKSet().toJSONObject();
	}

}
