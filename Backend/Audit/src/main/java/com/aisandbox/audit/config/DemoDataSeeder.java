package com.aisandbox.audit.config;

import com.aisandbox.audit.model.AuditLog;
import com.aisandbox.audit.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Seeds a handful of realistic audit-log rows on startup so a reviewer running the service for
 * the first time (or the future audit dashboard) sees real data immediately, not an empty table.
 *
 * <p>LOCAL/DEV only — SIT/UAT/PROD never seed. Skips seeding if the table already has rows
 * (idempotent across restarts against a persistent DEV database; LOCAL's in-memory H2 resets
 * every restart anyway). Disable with {@code demo.data.seed.enabled=false}.
 */
@Component
@Profile({"LOCAL", "DEV"})
public class DemoDataSeeder implements CommandLineRunner {

	private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

	private final AuditLogRepository repository;
	private final boolean enabled;

	public DemoDataSeeder(AuditLogRepository repository,
			@Value("${demo.data.seed.enabled:true}") boolean enabled) {
		this.repository = repository;
		this.enabled = enabled;
	}

	@Override
	public void run(String... args) {
		if (!enabled) {
			return;
		}
		if (repository.count() > 0) {
			log.info("Skipping demo data seed — audit_logs already has rows");
			return;
		}
		// This app's own domain events (auth + AI features), not generic e-commerce rows — so a
		// freshly-seeded dashboard reflects what AI-Sandbox actually does. Details mirror the real
		// events' non-PII key=value shape.
		List<AuditLog> seed = List.of(
			new AuditLog("User", "LOGIN", "Demo user signed in"),
			new AuditLog("User", "TOKEN_REFRESH", "Access token refreshed"),
			new AuditLog("User", "LOGOUT", "Demo user signed out"),
			new AuditLog("User", "LOGIN", "Recruiter demo login"),
			new AuditLog("Assistant", "CHAT", "blocked=false model=claude-opus-4-8 latencyMs=1820 retrievedChunks=5"),
			new AuditLog("Assistant", "CHAT", "blocked=false model=claude-opus-4-8 latencyMs=2450 retrievedChunks=3"),
			new AuditLog("Assistant", "CHAT", "blocked=true category=EMAIL"),
			new AuditLog("Flashcards", "GENERATED", "model=claude-opus-4-8 requested=8 produced=8 latencyMs=6120"),
			new AuditLog("Flashcards", "GENERATED", "model=claude-opus-4-8 requested=5 produced=5 latencyMs=4300"),
			new AuditLog("Rag", "SEARCH", "tool=search_knowledge latencyMs=38 error=false"),
			new AuditLog("Rag", "SEARCH", "tool=search_knowledge latencyMs=52 error=false"),
			new AuditLog("Mcp", "TOOL_CALL", "tool=list_sources latencyMs=7 error=false")
		);
		repository.saveAll(seed);
		log.info("Seeded {} demo audit log rows", seed.size());
	}

}
