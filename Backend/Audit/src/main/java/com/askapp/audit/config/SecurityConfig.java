package com.askapp.audit.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

// Active in every profile except LOADTEST, which swaps in an auth-free chain
// (LoadTestSecurityConfig) so the CI load generator can exercise the real endpoints. Method
// security (@PreAuthorize) is therefore also inactive under LOADTEST, consistent with every
// other endpoint being permitAll there.
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Profile("!LOADTEST")
public class SecurityConfig {

	private final List<String> allowedOrigins;

	public SecurityConfig(@Value("${cors.allowed-origins:http://localhost:4200}") List<String> allowedOrigins) {
		this.allowedOrigins = allowedOrigins;
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
			.csrf(csrf -> csrf.disable())
			.cors(cors -> cors.configurationSource(corsConfigurationSource()))
			.sessionManagement(session -> session
				.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/actuator/**").permitAll()
				// MCP server (RAG over repo docs). Unauthenticated by design: the corpus is
				// this repo's own public documentation — zero audit rows, zero user data —
				// so retrieval has nothing the JWT roles protect. ADR-0010 records the
				// trigger to revisit (any non-public data entering the corpus).
				.requestMatchers("/mcp").permitAll()
				.anyRequest().authenticated()
			)
			.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
		return http.build();
	}

	/**
	 * Auth stamps roles onto the JWT as a {@code roles} claim holding already-prefixed values
	 * (e.g. {@code "ROLE_ADMIN"} — see {@code TokenService.buildAccessToken}), so the converter
	 * maps that claim straight onto Spring Security authorities with no extra prefix, letting
	 * {@code @PreAuthorize("hasRole('ADMIN')")} work as-is.
	 */
	private JwtAuthenticationConverter jwtAuthenticationConverter() {
		JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
		authoritiesConverter.setAuthoritiesClaimName("roles");
		authoritiesConverter.setAuthorityPrefix("");
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
		return converter;
	}

	/** Origins allowed to call this API from a browser (the Angular SPA); configure via CORS_ALLOWED_ORIGINS. */
	private CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(allowedOrigins);
		configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		configuration.setAllowedHeaders(List.of("*"));
		configuration.setAllowCredentials(true);
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}

}
