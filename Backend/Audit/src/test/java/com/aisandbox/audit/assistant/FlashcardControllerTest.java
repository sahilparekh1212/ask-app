package com.aisandbox.audit.assistant;

import com.aisandbox.audit.assistant.dto.Flashcard;
import com.aisandbox.audit.assistant.dto.FlashcardDeck;
import com.aisandbox.audit.assistant.dto.FlashcardRequest;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FlashcardControllerTest {

	private final FlashcardService flashcardService = mock(FlashcardService.class);
	private final FlashcardController controller = new FlashcardController(flashcardService);

	private static Authentication withRole(String role) {
		return new UsernamePasswordAuthenticationToken("u", null, List.of(new SimpleGrantedAuthority(role)));
	}

	@Test
	void adminAuthorityGeneratesAdminScopedDeck() {
		FlashcardDeck deck = new FlashcardDeck(List.of(new Flashcard("q", "a")));
		when(flashcardService.generate(eq(5), eq(true))).thenReturn(deck);

		FlashcardDeck result = controller.flashcards(new FlashcardRequest(5), withRole("ROLE_ADMIN"));

		assertThat(result).isSameAs(deck);
		verify(flashcardService).generate(5, true);
	}

	@Test
	void userAuthorityIsNotAdmin() {
		when(flashcardService.generate(eq(3), eq(false)))
			.thenReturn(new FlashcardDeck(List.of(new Flashcard("q", "a"))));

		controller.flashcards(new FlashcardRequest(3), withRole("ROLE_USER"));

		verify(flashcardService).generate(3, false);
	}

	@Test
	void missingAuthenticationIsNonAdmin() {
		when(flashcardService.generate(eq(3), eq(false)))
			.thenReturn(new FlashcardDeck(List.of(new Flashcard("q", "a"))));

		controller.flashcards(new FlashcardRequest(3), null);

		verify(flashcardService).generate(3, false);
	}

}
