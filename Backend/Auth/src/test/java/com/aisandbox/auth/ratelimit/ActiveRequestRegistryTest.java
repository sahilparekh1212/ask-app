package com.aisandbox.auth.ratelimit;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class ActiveRequestRegistryTest {

	private final ActiveRequestRegistry registry = new ActiveRequestRegistry();

	@Test
	void register_firstRequestForAKeyIsNotDiscarded() {
		ActiveRequest req = registry.register("k1");

		assertThat(req.isDiscarded()).isFalse();
	}

	@Test
	void register_secondRequestForSameKeyDiscardsTheFirst() {
		ActiveRequest first = registry.register("k1");
		ActiveRequest second = registry.register("k1");

		assertThat(first.isDiscarded()).isTrue();
		assertThat(second.isDiscarded()).isFalse();
	}

	@Test
	void register_differentKeysDoNotInterfere() {
		ActiveRequest a = registry.register("k1");
		ActiveRequest b = registry.register("k2");

		assertThat(a.isDiscarded()).isFalse();
		assertThat(b.isDiscarded()).isFalse();
	}

	@Test
	void complete_doesNotClobberANewerWinnerRegisteredForTheSameKey() {
		ActiveRequest first = registry.register("k1");
		ActiveRequest second = registry.register("k1"); // discards `first`

		registry.complete(first);
		ActiveRequest third = registry.register("k1");

		assertThat(second.isDiscarded()).isTrue();
		assertThat(third.isDiscarded()).isFalse();
	}

	@Test
	void complete_removesTheActiveEntryWhenStillCurrent() {
		ActiveRequest req = registry.register("k1");

		registry.complete(req);
		ActiveRequest next = registry.register("k1");

		assertThat(next.isDiscarded()).isFalse();
	}

	@Test
	void register_concurrentRegistrationForSameKeyInterruptsTheLosersWorkerThread() throws InterruptedException {
		CountDownLatch registered = new CountDownLatch(1);
		AtomicBoolean interrupted = new AtomicBoolean(false);

		Thread worker = new Thread(() -> {
			registry.register("shared-key");
			registered.countDown();
			try {
				Thread.sleep(5000);
			} catch (InterruptedException e) {
				interrupted.set(true);
			}
		});
		worker.start();

		assertThat(registered.await(2, TimeUnit.SECONDS)).isTrue();
		registry.register("shared-key"); // newest wins: discards and interrupts the worker
		worker.join(2000);

		assertThat(interrupted.get()).isTrue();
		assertThat(worker.isAlive()).isFalse();
	}
}
