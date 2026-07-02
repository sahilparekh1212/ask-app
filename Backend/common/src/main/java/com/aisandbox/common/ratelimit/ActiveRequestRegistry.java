package com.aisandbox.common.ratelimit;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Lock-free registry of the single active request per (user, endpoint) key.
 *
 * <p>Backed by {@link ConcurrentHashMap} and built entirely on its atomic
 * {@code compute}/{@code remove} primitives, so it scales under high concurrency
 * without any explicit locking.
 */
@Component
public class ActiveRequestRegistry {

	private final ConcurrentHashMap<String, ActiveRequest> active = new ConcurrentHashMap<>();

	/**
	 * Register the calling thread as the active request for {@code key},
	 * discarding (cancelling) any request already in flight for the same key.
	 *
	 * <p>The check-discard-replace happens inside {@code compute}, which holds the
	 * bin lock for that key, so two concurrent requests for the same key cannot
	 * both believe they won.
	 */
	public ActiveRequest register(String key) {
		ActiveRequest mine = new ActiveRequest(key, Thread.currentThread());
		active.compute(key, (k, existing) -> {
			if (existing != null) {
				existing.discard(); // newest wins: cancel the older in-flight request
			}
			return mine;
		});
		return mine;
	}

	/**
	 * Remove this request from the registry, but only if it is still the active
	 * one. If a newer request already replaced it, this is a no-op so we never
	 * clobber the winner's entry.
	 */
	public void complete(ActiveRequest req) {
		req.markFinished();
		active.remove(req.getKey(), req);
	}

}
