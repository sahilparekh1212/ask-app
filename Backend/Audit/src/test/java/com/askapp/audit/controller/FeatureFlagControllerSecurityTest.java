package com.askapp.audit.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Confirms {@code GET /api/v1/meta/flags} is authenticated (any signed-in role, no ADMIN needed)
 * and serves the Liquibase-seeded flags. The plain {@link FeatureFlagControllerTest} can't exercise
 * the security rule — that only kicks in behind the real Spring Security filter chain.
 */
@SpringBootTest
@AutoConfigureMockMvc
class FeatureFlagControllerSecurityTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void flagsRequireAuthentication() throws Exception {
		mockMvc.perform(get("/api/v1/meta/flags"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void flagsAreServedToAnyAuthenticatedUser() throws Exception {
		mockMvc.perform(get("/api/v1/meta/flags")
				.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
			.andExpect(status().isOk())
			// The four seeded flags, key-ordered; chat is first.
			.andExpect(jsonPath("$[0].key").value("chat"))
			.andExpect(jsonPath("$[0].enabled").value(true))
			.andExpect(jsonPath("$.length()").value(4));
	}

}
