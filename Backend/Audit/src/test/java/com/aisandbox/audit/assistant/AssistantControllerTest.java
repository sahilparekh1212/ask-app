package com.aisandbox.audit.assistant;

import com.aisandbox.audit.assistant.dto.ChatRequest;
import com.aisandbox.audit.assistant.dto.ChatResponse;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssistantControllerTest {

	private final AssistantService assistantService = mock(AssistantService.class);
	private final AssistantController controller = new AssistantController(assistantService);

	private static Authentication withRole(String role) {
		return new UsernamePasswordAuthenticationToken("user", null,
			List.of(new SimpleGrantedAuthority(role)));
	}

	@Test
	void adminAuthorityIsPassedAsAdminFlag() {
		ChatRequest request = new ChatRequest("hi", null);
		when(assistantService.chat(any(), eq(true))).thenReturn(ChatResponse.of("ok"));

		ChatResponse response = controller.chat(request, withRole("ROLE_ADMIN"));

		assertThat(response.reply()).isEqualTo("ok");
		verify(assistantService).chat(request, true);
	}

	@Test
	void userAuthorityIsNotAdmin() {
		ChatRequest request = new ChatRequest("hi", null);
		when(assistantService.chat(any(), eq(false))).thenReturn(ChatResponse.of("ok"));

		controller.chat(request, withRole("ROLE_USER"));

		verify(assistantService).chat(request, false);
	}

	@Test
	void missingAuthenticationIsTreatedAsNonAdmin() {
		ChatRequest request = new ChatRequest("hi", null);
		when(assistantService.chat(any(), eq(false))).thenReturn(ChatResponse.of("ok"));

		controller.chat(request, null);

		verify(assistantService).chat(request, false);
	}

}
