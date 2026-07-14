package com.askapp.auth.service;

import java.util.Optional;

/**
 * Stores issued refresh tokens and consumes them exactly once.
 *
 * <p>Two implementations are wired by the {@code auth.refresh-token.store} property:
 * {@link InMemoryRefreshTokenStore} (default — single-replica/dev) and
 * {@link RedisRefreshTokenStore} (shared across horizontally-scaled Auth pods).
 *
 * <p>{@link #consume(String)} MUST be atomic get-and-delete so a refresh token is single-use even
 * under concurrent racing consumers — {@code ConcurrentHashMap.remove} for the in-memory store,
 * Redis {@code GETDEL} for the Redis store.
 */
public interface RefreshTokenStore {

	/** Persist {@code entry} under {@code token}, expiring it at {@link RefreshTokenEntry#expiresAt()}. */
	void store(String token, RefreshTokenEntry entry);

	/**
	 * Atomically fetch and delete the entry for {@code token}. Returns empty if the token is
	 * unknown, already consumed, or expired.
	 */
	Optional<RefreshTokenEntry> consume(String token);

	/** Delete {@code token} without returning it (logout). No-op if it was never issued. */
	void revoke(String token);
}
