package com.aisandbox.audit.service;

import com.aisandbox.audit.model.AuditLog;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Produces randomized-but-realistic audit rows for the demo endpoint, using the same
 * entityType/action vocabulary as the startup {@code DemoDataSeeder} so generated rows blend
 * with the seeded ones (and exercise the dashboard's dropdowns, stats and details search).
 *
 * <p>The vocabulary is <em>this application's own domain events</em> — the auth events Auth
 * emits (User LOGIN/TOKEN_REFRESH/LOGOUT) and the AI-feature events Audit emits (Assistant/CHAT,
 * Flashcards/GENERATED, Rag/SEARCH, Mcp/TOOL_CALL) — so a demo-populated dashboard tells the same
 * story real traffic would, not a generic e-commerce one. Details mirror the real events' non-PII
 * {@code key=value} shape (model, latency, counts); no message, query, or card text.
 *
 * <p>LOCAL/DEV only, like the seeder — dummy rows have no business in a real audit trail.
 */
@Component
@Profile({"LOCAL", "DEV"})
public class DemoDataGenerator {

	/**
	 * One weighted-equal template per row. {@code %d} (if present) receives a random number used
	 * as a user id or a latency in ms; a pattern with no {@code %d} is emitted verbatim.
	 */
	private record Template(String entityType, String action, String detailsPattern) {
	}

	private static final List<Template> TEMPLATES = List.of(
		new Template("User", "LOGIN", "User #%d signed in"),
		new Template("User", "TOKEN_REFRESH", "Access token refreshed for user #%d"),
		new Template("User", "LOGOUT", "User #%d signed out"),
		new Template("Assistant", "CHAT", "blocked=false model=claude-opus-4-8 latencyMs=%d retrievedChunks=5"),
		new Template("Assistant", "CHAT", "blocked=false model=claude-opus-4-8 latencyMs=%d retrievedChunks=3"),
		new Template("Assistant", "CHAT", "blocked=true category=CREDENTIAL"),
		new Template("Flashcards", "GENERATED", "model=claude-opus-4-8 requested=8 produced=8 latencyMs=%d"),
		new Template("Rag", "SEARCH", "tool=search_knowledge latencyMs=%d error=false"),
		new Template("Mcp", "TOOL_CALL", "tool=list_sources latencyMs=%d error=false")
	);

	public List<AuditLog> generate(int count) {
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		List<AuditLog> rows = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			Template t = TEMPLATES.get(rnd.nextInt(TEMPLATES.size()));
			rows.add(new AuditLog(t.entityType(), t.action(), t.detailsPattern().formatted(rnd.nextInt(1000, 10000))));
		}
		return rows;
	}

}
