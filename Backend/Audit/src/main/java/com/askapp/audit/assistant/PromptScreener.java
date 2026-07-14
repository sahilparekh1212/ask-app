package com.askapp.audit.assistant;

import com.askapp.audit.assistant.dto.ChatRequest;
import com.askapp.audit.assistant.dto.ChatTurn;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Server-side screen that runs over every inbound chat message (and every replayed history
 * turn) <em>before</em> anything is sent to the LLM provider. If a pattern matches, the
 * request is answered locally with a canned refusal and never forwarded — the provider must
 * not receive credentials or personal data, even by user mistake (e.g. pasting a JWT while
 * asking "why doesn't this token work?").
 *
 * <p>Only the violation <em>category</em> is ever logged, never the matched text, so the
 * sensitive value doesn't just move from the provider's logs into ours.
 */
@Component
public class PromptScreener {

	/**
	 * Screening rules, in evaluation order. Category name → pattern.
	 * <ul>
	 *   <li><b>token</b> — JWT-shaped strings ({@code eyJ...} base64url segments) and
	 *       {@code Bearer}-prefixed values.</li>
	 *   <li><b>credential</b> — a credential keyword being <em>assigned a value</em>
	 *       ({@code password=hunter2}, {@code api_key: sk-...}). A keyword alone ("how does
	 *       the password flow work?") is a legitimate question and passes.</li>
	 *   <li><b>email</b> — email addresses (PII; also the audit rows' likely identifier).</li>
	 *   <li><b>card</b> — 13–19 digit runs with optional space/dash separators.</li>
	 * </ul>
	 */
	private static final Map<String, Pattern> RULES = new LinkedHashMap<>();
	static {
		RULES.put("token", Pattern.compile("eyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{4,}|(?i)\\bBearer\\s+[A-Za-z0-9._~+/-]{16,}"));
		RULES.put("credential", Pattern.compile(
			"(?i)\\b(password|passwd|pwd|secret|api[_-]?key|access[_-]?token|refresh[_-]?token|private[_-]?key|client[_-]?secret)\\b\\s*[:=]\\s*\\S+"));
		RULES.put("email", Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"));
		RULES.put("card", Pattern.compile("\\b\\d(?:[ -]?\\d){12,18}\\b"));
	}

	/**
	 * Returns the violation category of the first rule matching the message or any history
	 * turn, or empty when the request is clean and may be forwarded.
	 */
	public Optional<String> firstViolation(ChatRequest request) {
		Optional<String> inMessage = firstViolation(request.message());
		if (inMessage.isPresent()) {
			return inMessage;
		}
		for (ChatTurn turn : request.historyOrEmpty()) {
			Optional<String> inTurn = firstViolation(turn.content());
			if (inTurn.isPresent()) {
				return inTurn;
			}
		}
		return Optional.empty();
	}

	private Optional<String> firstViolation(String text) {
		return RULES.entrySet().stream()
			.filter(rule -> rule.getValue().matcher(text).find())
			.map(Map.Entry::getKey)
			.findFirst();
	}

}
