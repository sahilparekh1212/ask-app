package com.askapp.audit.ratelimit;

import com.askapp.common.ratelimit.ActiveRequestRegistry;
import com.askapp.common.ratelimit.RateLimitInterceptor;
import com.askapp.common.ratelimit.RateLimitProperties;
import com.askapp.common.ratelimit.RequestDiscardedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@code DiscardContext.set}/{@code ActiveRequest}'s constructor are package-private to
 * {@code common.ratelimit}, so — unlike before the ratelimit package moved to {@code :common} —
 * this test can no longer construct them directly. It drives the same scenario through the real
 * {@link RateLimitInterceptor}/{@link ActiveRequestRegistry} public API instead.
 */
class TransactionalRequestExecutorTest {

	private static final String KEY = "anonymous|POST|/api/v1/audit-logs";

	private final TransactionalRequestExecutor executor = new TransactionalRequestExecutor();
	private final ActiveRequestRegistry registry = new ActiveRequestRegistry();
	private final RateLimitInterceptor interceptor = new RateLimitInterceptor(registry, new RateLimitProperties());

	@AfterEach
	void cleanUp() {
		// afterCompletion() is the public way to clear DiscardContext (it's package-private in
		// common.ratelimit) and the worker thread's interrupt flag.
		interceptor.afterCompletion(mock(HttpServletRequest.class), mock(HttpServletResponse.class), new Object(), null);
	}

	private void registerCurrentThreadAsTheActiveRequest() {
		HttpServletRequest request = mock(HttpServletRequest.class);
		when(request.getMethod()).thenReturn("POST");
		when(request.getRequestURI()).thenReturn("/api/v1/audit-logs");
		interceptor.preHandle(request, mock(HttpServletResponse.class), new Object());
	}

	@Test
	void run_returnsTheWorkResultWhenNotDiscarded() {
		registerCurrentThreadAsTheActiveRequest();

		String result = executor.run(() -> "saved");

		assertThat(result).isEqualTo("saved");
	}

	@Test
	void run_throwsAfterExecutingTheWorkWhenSupersededBeforeCheckpoint() {
		registerCurrentThreadAsTheActiveRequest();
		boolean[] workRan = {false};

		registry.register(KEY); // simulate a newer request superseding this one mid-flight
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
