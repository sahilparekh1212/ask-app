package com.askapp.audit.assistant;

import com.askapp.audit.assistant.dto.ChatRequest;
import com.askapp.audit.assistant.dto.ChatResponse;
import com.askapp.audit.assistant.dto.SpeakRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
	private final SpeechService speechService;

	public AssistantController(AssistantService assistantService, SpeechService speechService) {
		this.assistantService = assistantService;
		this.speechService = speechService;
	}

	@PostMapping("/chat")
	@Operation(summary = "Ask the assistant about the application. "
		+ "ADMIN sees answers grounded on recent audit rows; USER on aggregate stats only.")
	public ChatResponse chat(@Valid @RequestBody ChatRequest request, Authentication authentication) {
		return assistantService.chat(request, isAdmin(authentication));
	}

	@PostMapping(value = "/speak", produces = "audio/mpeg")
	@Operation(summary = "Read an assistant reply aloud with a natural neural voice via server-side "
		+ "Google Cloud Text-to-Speech. Returns MP3 audio; 503 when no TTS key is configured, so the "
		+ "SPA falls back to the browser's built-in voice.")
	public ResponseEntity<byte[]> speak(@Valid @RequestBody SpeakRequest request) {
		byte[] audio = speechService.synthesize(request.text());
		return ResponseEntity.ok()
			.contentType(MediaType.parseMediaType("audio/mpeg"))
			.cacheControl(CacheControl.noStore())
			.body(audio);
	}

	private boolean isAdmin(Authentication authentication) {
		return authentication != null && authentication.getAuthorities().stream()
			.map(GrantedAuthority::getAuthority)
			.anyMatch("ROLE_ADMIN"::equals);
	}

}
