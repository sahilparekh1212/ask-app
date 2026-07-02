package com.aisandbox.audit.ratelimit;

import com.aisandbox.common.ratelimit.DiscardContext;
import com.aisandbox.common.ratelimit.RequestDiscardedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Supplier;

/**
 * Runs a mutating unit of work inside a transaction that is rolled back if this
 * request is discarded (superseded) before it commits.
 *
 * <p>The {@link DiscardContext#checkpoint()} call runs <em>after</em> the work but
 * <em>inside</em> the same transaction. If a newer request for the same
 * (user, endpoint) key arrived while this one was executing, the checkpoint throws
 * {@link RequestDiscardedException}, and because the throw escapes the
 * {@code @Transactional} boundary the transaction rolls back — so a discarded
 * request leaves no partial writes behind.
 */
@Component
public class TransactionalRequestExecutor {

	@Transactional(rollbackFor = RequestDiscardedException.class)
	public <T> T run(Supplier<T> work) {
		T result = work.get();
		DiscardContext.checkpoint();
		return result;
	}

}
