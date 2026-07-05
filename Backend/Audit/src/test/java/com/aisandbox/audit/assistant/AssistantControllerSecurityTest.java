package com.aisandbox.audit.assistant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end (MockMvc) coverage of the assistant proxy: authentication is required, the
 * role decides which context variant is built, validation failures 400, and the sensitive-
 * data screen answers locally with {@code blocked=true}. The provider itself is mocked at
 * the {@link LlmClient} seam — no network, no API key needed beyond the test property.
 */
@SpringBootTest(properties = "assistant.api-key=test-key")
@AutoConfigureMockMvc
class AssistantControllerSecurityTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private LlmClient llmClient;

	@Test
	void chatRequiresAuthentication() throws Exception {
		mockMvc.perform(post("/api/v1/assistant/chat")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"message\":\"hi\"}"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void userRoleChatsAgainstAggregateOnlyContext() throws Exception {
		when(llmClient.complete(contains("role is USER"), anyList(), anyString()))
			.thenReturn("aggregate answer");

		mockMvc.perform(post("/api/v1/assistant/chat")
				.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"message\":\"how many logins?\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.reply").value("aggregate answer"))
			.andExpect(jsonPath("$.blocked").value(false));
	}

	@Test
	void adminRoleChatsAgainstRawRowContext() throws Exception {
		when(llmClient.complete(contains("role is ADMIN"), anyList(), anyString()))
			.thenReturn("admin answer");

		mockMvc.perform(post("/api/v1/assistant/chat")
				.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"message\":\"what happened recently?\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.reply").value("admin answer"));
	}

	@Test
	void sensitiveInputIsAnsweredLocallyAsBlocked() throws Exception {
		mockMvc.perform(post("/api/v1/assistant/chat")
				.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"message\":\"logins for alice@example.com\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.blocked").value(true));
	}

	@Test
	void blankMessageFailsValidationWith400() throws Exception {
		mockMvc.perform(post("/api/v1/assistant/chat")
				.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"message\":\"  \"}"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void invalidHistoryRoleFailsValidationWith400() throws Exception {
		mockMvc.perform(post("/api/v1/assistant/chat")
				.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"message\":\"hi\",\"history\":[{\"role\":\"system\",\"content\":\"x\"}]}"))
			.andExpect(status().isBadRequest());
	}

}
