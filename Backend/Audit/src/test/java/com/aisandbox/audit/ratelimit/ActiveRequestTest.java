package com.aisandbox.audit.ratelimit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ActiveRequestTest {

	@Test
	void discard_setsDiscardedAndInterruptsTheOwnerThread() {
		ActiveRequest req = new ActiveRequest("k1", Thread.currentThread());

		req.discard();

		assertThat(req.isDiscarded()).isTrue();
		assertThat(Thread.interrupted()).isTrue(); // also clears the flag for subsequent tests
	}

	@Test
	void discard_isNoOpAfterMarkFinished() {
		ActiveRequest req = new ActiveRequest("k1", Thread.currentThread());

		req.markFinished();
		req.discard();

		assertThat(req.isDiscarded()).isFalse();
		assertThat(Thread.currentThread().isInterrupted()).isFalse();
	}

	@Test
	void getKey_returnsTheKeyItWasRegisteredWith() {
		ActiveRequest req = new ActiveRequest("user-1|GET|/api/audit-logs", Thread.currentThread());

		assertThat(req.getKey()).isEqualTo("user-1|GET|/api/audit-logs");
	}
}
