package com.askapp.auth.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Process-local refresh-token store. The default when {@code auth.refresh-token.store} is unset or
 * {@code in-memory} — correct for a single replica and for tests, but a token issued by one pod is
 * invisible to another (see {@code ADR-0007}); use {@link RedisRefreshTokenStore} to scale past one.
 *
 * <p>{@link #consume(String)} relies on {@link ConcurrentHashMap#remove(Object)} being atomic per
 * key, so two threads racing the same token can never both succeed (proven in
 * {@code TokenServiceTest.consumeRefreshToken_isSingleUseUnderConcurrentRacingConsumers}).
 */
@Component
@ConditionalOnProperty(name = "auth.refresh-token.store", havingValue = "in-memory", matchIfMissing = true)
public class InMemoryRefreshTokenStore implements RefreshTokenStore {

	private final ConcurrentHashMap<String, RefreshTokenEntry> store = new ConcurrentHashMap<>();

	@Override
	public void store(String token, RefreshTokenEntry entry) {
		store.put(token, entry);
	}

	@Override
	public Optional<RefreshTokenEntry> consume(String token) {
		RefreshTokenEntry entry = store.remove(token);
		if (entry == null || entry.expiresAt().isBefore(Instant.now())) {
			return Optional.empty();
		}
		return Optional.of(entry);
	}

	@Override
	public void revoke(String token) {
		store.remove(token);
	}
}
