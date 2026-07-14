package com.askapp.audit.assistant;

import com.askapp.audit.assistant.dto.ChatRequest;
import com.askapp.audit.assistant.dto.ChatResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Server-side proxy for the LLM chat assistant. The SPA never talks to the LLM provider
 * directly: the API key lives only in this service's environment, and the caller's JWT is
 * used solely to authenticate here and derive the role — it is never forwarded upstream.
 * Both roles may chat; the role decides how much live data the context builder exposes.
 */
@RestController
@RequestMapping("/api/v1/assistant")
@Tag(name = "Assistant", description = "LLM chat about this application (server-side Claude proxy)")
public class AssistantController {

	private final AssistantService assistantService;

	public AssistantController(AssistantService assistantService) {
		this.assistantService = assistantService;
	}

	@PostMapping("/chat")
	@Operation(summary = "Ask the assistant about the application. "
		+ "ADMIN sees answers grounded on recent audit rows; USER on aggregate stats only.")
	public ChatResponse chat(@Valid @RequestBody ChatRequest request, Authentication authentication) {
		return assistantService.chat(request, isAdmin(authentication));
	}

	private boolean isAdmin(Authentication authentication) {
		return authentication != null && authentication.getAuthorities().stream()
			.map(GrantedAuthority::getAuthority)
			.anyMatch("ROLE_ADMIN"::equals);
	}

}
