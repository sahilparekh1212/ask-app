package com.aisandbox.audit.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security for the {@code LOADTEST} profile ONLY: authentication is disabled so the k6 load
 * generator in CI can hit the real endpoints without minting JWTs against the Auth service.
 *
 * <p>This is selected exclusively by {@code SPRING_PROFILES_ACTIVE=...,LOADTEST}, which is set
 * only in the load-test CI job — never in DEV/SIT/UAT/PROD. The production-shaped
 * {@link SecurityConfig} (JWT-authenticated) is active in every other profile.
 */
@Configuration
@EnableWebSecurity
@Profile("LOADTEST")
public class LoadTestSecurityConfig {

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
			.csrf(csrf -> csrf.disable())
			.sessionManagement(session -> session
				.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
		return http.build();
	}

}
