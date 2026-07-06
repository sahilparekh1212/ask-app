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
 * End-to-end (MockMvc) coverage of the flashcard endpoint: auth required, role scopes the
 * grounding context, out-of-range count 400s, and the deck is returned. The provider is
 * mocked at the {@link LlmClient} seam so no network or real key is needed.
 */
@SpringBootTest(properties = "assistant.api-key=test-key")
@AutoConfigureMockMvc
class FlashcardControllerSecurityTest {

	private static final String DECK_JSON =
		"{\"cards\":[{\"question\":\"What is Audit?\",\"answer\":\"It persists audit rows.\"}]}";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private LlmClient llmClient;

	@Test
	void requiresAuthentication() throws Exception {
		mockMvc.perform(post("/api/v1/assistant/flashcards")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"count\":5}"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void userRoleGeneratesFromAggregateOnlyContext() throws Exception {
		when(llmClient.complete(contains("role is USER"), anyList(), anyString())).thenReturn(DECK_JSON);

		mockMvc.perform(post("/api/v1/assistant/flashcards")
				.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"count\":5}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.cards[0].question").value("What is Audit?"));
	}

	@Test
	void adminRoleGeneratesFromRawRowContext() throws Exception {
		when(llmClient.complete(contains("role is ADMIN"), anyList(), anyString())).thenReturn(DECK_JSON);

		mockMvc.perform(post("/api/v1/assistant/flashcards")
				.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"count\":5}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.cards[0].answer").value("It persists audit rows."));
	}

	@Test
	void outOfRangeCountFailsValidationWith400() throws Exception {
		mockMvc.perform(post("/api/v1/assistant/flashcards")
				.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"count\":50}"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void missingCountFailsValidationWith400() throws Exception {
		mockMvc.perform(post("/api/v1/assistant/flashcards")
				.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isBadRequest());
	}

}
