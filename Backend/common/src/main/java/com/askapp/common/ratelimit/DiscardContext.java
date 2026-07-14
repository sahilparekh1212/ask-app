package com.askapp.common.ratelimit;

/**
 * Per-thread handle to the current request's {@link ActiveRequest}.
 *
 * <p>In the servlet model one request maps to exactly one worker thread, so a
 * {@link ThreadLocal} is the correct, contention-free way to expose the active
 * request to the transactional service layer without threading it through every
 * method signature.
 */
public final class DiscardContext {

	private static final ThreadLocal<ActiveRequest> CURRENT = new ThreadLocal<>();

	private DiscardContext() {
	}

	static void set(ActiveRequest request) {
		CURRENT.set(request);
	}

	static void clear() {
		CURRENT.remove();
	}

	public static ActiveRequest current() {
		return CURRENT.get();
	}

	/**
	 * Transactional checkpoint: throws {@link RequestDiscardedException} if a newer
	 * request has superseded this one. Call this inside a {@code @Transactional}
	 * method after mutating work so the throw rolls the transaction back.
	 */
	public static void checkpoint() {
		ActiveRequest request = CURRENT.get();
		if (request != null && request.isDiscarded()) {
			throw new RequestDiscardedException(request.getKey());
		}
	}

}
