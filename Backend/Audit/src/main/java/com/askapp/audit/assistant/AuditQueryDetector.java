package com.askapp.audit.assistant;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Decides whether a chat question is about the application's <em>live state</em> — what users and
 * agents have actually done (the audit trail) — as opposed to its design, architecture, or how to
 * use it. Only state-related questions get the live audit stats/rows attached to the prompt (see
 * {@link AssistantContextBuilder}); a generic "why is there no API gateway?" stays docs-only.
 *
 * <p>Deliberately a keyword heuristic, in the spirit of {@link PromptScreener}: it's simple,
 * fast, and transparent, and it fails <em>safe</em> — an unmatched query just means the model
 * answers from docs/RAG without live numbers (and, per the prompt rules, says so if asked for a
 * figure it wasn't given). The alternative — an extra LLM classification round-trip, or letting
 * the model pull stats via a tool — buys accuracy at the cost of latency/complexity this one
 * call site doesn't justify.
 */
@Component
public class AuditQueryDetector {

	// Word-boundary-anchored signals that a question is about recorded activity / counts / recency.
	// Kept narrow on purpose so design questions ("what's the tech stack?", "which files do X?")
	// don't trip it. \b protects against substrings (e.g. "account" won't match "count").
	private static final Pattern STATE_SIGNALS = Pattern.compile(
		"\\b(how many|how often|how much|number of|counts?|most (frequent|common|active)|busiest"
			+ "|top \\d|recent|recently|latest|last \\d|today|so far|lately|right now|currently"
			+ "|happened|happening|going on|logins?|logged in|sign[- ]?ins?|activity|traffic"
			+ "|events?|actions?|audit (log|logs|data|rows|trail|entries)"
			+ "|dashboard (data|numbers|stats)|statistics|stats|who (logged|did|has|used)|when did)\\b",
		Pattern.CASE_INSENSITIVE);

	/** True when the question looks like it's asking about the running system's recorded activity. */
	public boolean isAboutState(String query) {
		return query != null && STATE_SIGNALS.matcher(query).find();
	}

}
