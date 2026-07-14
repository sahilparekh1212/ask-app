package com.askapp.auth.config;

import io.micrometer.context.ContextRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskDecorator;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AsyncTracingConfigTest {

	@Test
	void decoratorCarriesThreadLocalContextOntoTheAsyncThread() throws Exception {
		// Stand-in for the Observation context: a ThreadLocal registered with the same
		// ContextRegistry mechanism Micrometer uses. The decorated task must see the
		// submitting thread's value even though it runs on a different thread.
		ThreadLocal<String> context = new ThreadLocal<>();
		ContextRegistry.getInstance()
			.registerThreadLocalAccessor("async-tracing-test", context::get, context::set, context::remove);

		TaskDecorator decorator = new AsyncTracingConfig().contextPropagatingTaskDecorator();
		context.set("propagated");
		AtomicReference<String> seenOnAsyncThread = new AtomicReference<>();
		Runnable decorated = decorator.decorate(() -> seenOnAsyncThread.set(context.get()));
		context.remove();

		Thread asyncThread = new Thread(decorated);
		asyncThread.start();
		asyncThread.join();

		assertThat(seenOnAsyncThread.get()).isEqualTo("propagated");
	}
}
