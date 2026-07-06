package com.aisandbox.audit.assistant;

import com.aisandbox.audit.assistant.dto.FlashcardDeck;
import com.aisandbox.audit.assistant.dto.FlashcardRequest;
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
 * Generates an LLM study deck about the application, through the same server-side Claude
 * proxy as the chat assistant (key stays server-side, no auth headers forwarded). The role
 * decides how much live data grounds the deck, exactly as for chat.
 */
@RestController
@RequestMapping("/api/v1/assistant")
@Tag(name = "Assistant", description = "LLM features about this application (server-side Claude proxy)")
public class FlashcardController {

	private final FlashcardService flashcardService;

	public FlashcardController(FlashcardService flashcardService) {
		this.flashcardService = flashcardService;
	}

	@PostMapping("/flashcards")
	@Operation(summary = "Generate a deck of Q&A flashcards explaining the application (count 1..20).")
	public FlashcardDeck flashcards(@Valid @RequestBody FlashcardRequest request, Authentication authentication) {
		return flashcardService.generate(request.count(), isAdmin(authentication));
	}

	private boolean isAdmin(Authentication authentication) {
		return authentication != null && authentication.getAuthorities().stream()
			.map(GrantedAuthority::getAuthority)
			.anyMatch("ROLE_ADMIN"::equals);
	}

}
