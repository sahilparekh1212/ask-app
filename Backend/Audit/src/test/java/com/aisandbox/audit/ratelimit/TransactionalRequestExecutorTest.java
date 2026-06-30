package com.aisandbox.audit.ratelimit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionalRequestExecutorTest {

	private final TransactionalRequestExecutor executor = new TransactionalRequestExecutor();

	@AfterEach
	void cleanUp() {
		DiscardContext.clear();
	}

	@Test
	void run_returnsTheWorkResultWhenNotDiscarded() {
		ActiveRequest current = new ActiveRequest("k1", Thread.currentThread());
		DiscardContext.set(current);

		String result = executor.run(() -> "saved");

		assertThat(result).isEqualTo("saved");
	}

	@Test
	void run_throwsAfterExecutingTheWorkWhenSupersededBeforeCheckpoint() {
		ActiveRequest current = new ActiveRequest("k1", Thread.currentThread());
		DiscardContext.set(current);
		boolean[] workRan = {false};

		current.discard(); // simulate a newer request superseding this one mid-flight
		Thread.interrupted(); // clear the interrupt flag discard() raised; not under test here

		assertThatThrownBy(() -> executor.run(() -> {
			workRan[0] = true;
			return "saved";
		})).isInstanceOf(RequestDiscardedException.class);

		assertThat(workRan[0]).isTrue();
	}

	@Test
	void run_withNoActiveRequestInContextBehavesAsANoOpCheckpoint() {
		String result = executor.run(() -> "saved");

		assertThat(result).isEqualTo("saved");
	}
}
