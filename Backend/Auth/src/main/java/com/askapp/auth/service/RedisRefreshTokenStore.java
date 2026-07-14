package com.askapp.auth.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Redis-backed refresh-token store, shared across every Auth replica — the piece that makes Auth
 * horizontally scalable (see {@code ADR-0007}). Active when {@code auth.refresh-token.store=redis}.
 *
 * <p>Single-use is guaranteed cross-replica by {@code GETDEL} (via
 * {@link org.springframework.data.redis.core.ValueOperations#getAndDelete}), a single atomic
 * fetch-and-delete, so two pods racing the same token can never both mint a new access token.
 * Redis's own key TTL expires stale tokens for free; the {@code expiresAt} check is a defensive
 * belt-and-suspenders in case a key outlives its TTL.
 */
@Component
@ConditionalOnProperty(name = "auth.refresh-token.store", havingValue = "redis")
public class RedisRefreshTokenStore implements RefreshTokenStore {

	/** Namespaced so refresh tokens never collide with any other keyspace in a shared Redis. */
	static final String KEY_PREFIX = "auth:refresh-token:";

	private final RedisTemplate<String, RefreshTokenEntry> redis;

	public RedisRefreshTokenStore(RedisTemplate<String, RefreshTokenEntry> refreshTokenRedisTemplate) {
		this.redis = refreshTokenRedisTemplate;
	}

	@Override
	public void store(String token, RefreshTokenEntry entry) {
		Duration ttl = Duration.between(Instant.now(), entry.expiresAt());
		if (ttl.isNegative() || ttl.isZero()) {
			return; // already expired — nothing worth storing
		}
		redis.opsForValue().set(KEY_PREFIX + token, entry, ttl);
	}

	@Override
	public Optional<RefreshTokenEntry> consume(String token) {
		RefreshTokenEntry entry = redis.opsForValue().getAndDelete(KEY_PREFIX + token);
		if (entry == null || entry.expiresAt().isBefore(Instant.now())) {
			return Optional.empty();
		}
		return Optional.of(entry);
	}

	@Override
	public void revoke(String token) {
		redis.delete(KEY_PREFIX + token);
	}
}
