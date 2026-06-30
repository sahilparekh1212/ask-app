package com.aisandbox.audit.ratelimit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiscardContextTest {

	@AfterEach
	void cleanUp() {
		DiscardContext.clear();
		Thread.interrupted();
	}

	@Test
	void currentReflectsSetAndClear() {
		ActiveRequest request = new ActiveRequest("k1", Thread.currentThread());

		DiscardContext.set(request);
		assertThat(DiscardContext.current()).isSameAs(request);

		DiscardContext.clear();
		assertThat(DiscardContext.current()).isNull();
	}

	@Test
	void checkpointThrowsWhenTheCurrentRequestWasDiscarded() {
		ActiveRequest request = new ActiveRequest("k1", Thread.currentThread());
		DiscardContext.set(request);
		request.discard();
		Thread.interrupted(); // clear the interrupt discard() raised; not under test

		assertThatThrownBy(DiscardContext::checkpoint)
			.isInstanceOf(RequestDiscardedException.class);
	}

	@Test
	void checkpointIsANoOpWhenAbsentOrStillCurrent() {
		assertThatCode(DiscardContext::checkpoint).doesNotThrowAnyException();

		DiscardContext.set(new ActiveRequest("k1", Thread.currentThread()));
		assertThatCode(DiscardContext::checkpoint).doesNotThrowAnyException();
	}
}
