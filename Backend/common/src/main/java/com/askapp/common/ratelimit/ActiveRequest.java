package com.askapp.common.ratelimit;

/**
 * Handle to a single in-flight request for a (user, endpoint) key.
 *
 * <p>Holds the worker thread so a newer request can interrupt it ("newest wins").
 * All mutating methods are {@code synchronized} on this instance so that
 * {@link #discard()} and {@link #markFinished()} cannot race — we must never
 * interrupt a worker thread that has already finished and been returned to the
 * servlet pool (it could be serving an unrelated request by then).
 */
public final class ActiveRequest {

	private final String key;
	private final Thread owner;
	private volatile boolean discarded;
	private volatile boolean finished;

	ActiveRequest(String key, Thread owner) {
		this.key = key;
		this.owner = owner;
	}

	public String getKey() {
		return key;
	}

	/** True once a newer request for the same key has superseded this one. */
	public boolean isDiscarded() {
		return discarded;
	}

	/**
	 * Mark this request superseded and interrupt its worker so blocking I/O
	 * (DB calls, etc.) aborts promptly. No-op if the request already finished.
	 */
	synchronized void discard() {
		if (finished) {
			return;
		}
		discarded = true;
		owner.interrupt();
	}

	/** Mark the request done so a late {@link #discard()} becomes a no-op. */
	synchronized void markFinished() {
		finished = true;
	}

}
