package com.askapp.audit.assistant;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AuditQueryDetectorTest {

	private final AuditQueryDetector detector = new AuditQueryDetector();

	@Test
	void flagsQuestionsAboutLiveState() {
		List<String> stateQuestions = List.of(
			"How many logins were there today?",
			"What were the most frequent actions?",
			"Show me recent activity",
			"How many CHAT events so far?",
			"who logged in lately?",
			"what's in the audit log right now",
			"give me the dashboard stats");
		for (String q : stateQuestions) {
			assertThat(detector.isAboutState(q)).as("should flag: %s", q).isTrue();
		}
	}

	@Test
	void ignoresDesignAndHowToQuestions() {
		List<String> designQuestions = List.of(
			"What does the audit service do?",
			"Why is there no API gateway?",
			"Which files are tied to the Docker setup?",
			"Explain the rate limiter design",
			"What is the tech stack?", // 'stack' must not trip the 'stats' signal
			"How does RAG work in this app?",
			"What account roles exist?"); // 'account' must not trip the 'count' signal
		for (String q : designQuestions) {
			assertThat(detector.isAboutState(q)).as("should ignore: %s", q).isFalse();
		}
	}

	@Test
	void nullIsNotAboutState() {
		assertThat(detector.isAboutState(null)).isFalse();
	}

}
