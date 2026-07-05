package com.aisandbox.audit.assistant;

import com.aisandbox.audit.assistant.dto.ChatRequest;
import com.aisandbox.audit.assistant.dto.ChatTurn;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PromptScreenerTest {

	private final PromptScreener screener = new PromptScreener();

	private ChatRequest request(String message) {
		return new ChatRequest(message, null);
	}

	@Test
	void cleanQuestionPasses() {
		assertThat(screener.firstViolation(request("What does the audit service do?"))).isEmpty();
	}

	@Test
	void jwtShapedStringIsBlockedAsToken() {
		String jwt = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0In0.sig";
		assertThat(screener.firstViolation(request("why doesn't " + jwt + " work?"))).contains("token");
	}

	@Test
	void bearerValueIsBlockedAsToken() {
		assertThat(screener.firstViolation(request("I send Bearer abcdefghijklmnop1234 and get 401")))
			.contains("token");
	}

	@Test
	void credentialAssignmentIsBlocked() {
		assertThat(screener.firstViolation(request("my password=hunter2 doesn't work"))).contains("credential");
		assertThat(screener.firstViolation(request("set api_key: sk-ant-something"))).contains("credential");
	}

	@Test
	void credentialKeywordWithoutValuePasses() {
		// Asking ABOUT credentials is a legitimate question — only keyword+value is blocked.
		assertThat(screener.firstViolation(request("How does the password login flow work?"))).isEmpty();
	}

	@Test
	void emailAddressIsBlocked() {
		assertThat(screener.firstViolation(request("show logins for alice@example.com"))).contains("email");
	}

	@Test
	void cardLikeNumberIsBlockedEvenWithSeparators() {
		assertThat(screener.firstViolation(request("card 4111 1111 1111 1111 was charged"))).contains("card");
		assertThat(screener.firstViolation(request("card 4111-1111-1111-1111"))).contains("card");
	}

	@Test
	void shortDigitRunsPass() {
		assertThat(screener.firstViolation(request("show me the last 20 rows from 2026"))).isEmpty();
	}

	@Test
	void historyTurnsAreScreenedToo() {
		ChatRequest withDirtyHistory = new ChatRequest("and what about now?",
			List.of(new ChatTurn("user", "logins for bob@example.com please")));
		assertThat(screener.firstViolation(withDirtyHistory)).contains("email");
	}

}
