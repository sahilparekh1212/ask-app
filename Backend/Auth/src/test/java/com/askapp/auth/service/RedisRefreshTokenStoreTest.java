package com.askapp.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisRefreshTokenStoreTest {

	private static final String KEY = RedisRefreshTokenStore.KEY_PREFIX + "tok-1";

	@SuppressWarnings("unchecked")
	private final RedisTemplate<String, RefreshTokenEntry> redis = mock(RedisTemplate.class);
	@SuppressWarnings("unchecked")
	private final ValueOperations<String, RefreshTokenEntry> valueOps = mock(ValueOperations.class);

	private RedisRefreshTokenStore store;

	@BeforeEach
	void setUp() {
		when(redis.opsForValue()).thenReturn(valueOps);
		store = new RedisRefreshTokenStore(redis);
	}

	@Test
	void store_setsNamespacedKeyWithTtlUntilExpiry() {
		RefreshTokenEntry entry = entry(Instant.now().plus(Duration.ofDays(7)));

		store.store("tok-1", entry);

		ArgumentCaptor<Duration> ttl = ArgumentCaptor.forClass(Duration.class);
		verify(valueOps).set(eq(KEY), eq(entry), ttl.capture());
		// ~7 days, minus a hair for wall-clock elapsed between the entry's expiry and the set call.
		assertThat(ttl.getValue()).isBetween(Duration.ofDays(6), Duration.ofDays(7));
	}

	@Test
	void store_skipsAlreadyExpiredEntry() {
		store.store("tok-1", entry(Instant.now().minusSeconds(1)));

		verify(valueOps, never()).set(any(), any(), any(Duration.class));
	}

	@Test
	void consume_returnsEntryViaAtomicGetAndDelete() {
		RefreshTokenEntry entry = entry(Instant.now().plus(Duration.ofDays(1)));
		when(valueOps.getAndDelete(KEY)).thenReturn(entry);

		Optional<RefreshTokenEntry> result = store.consume("tok-1");

		assertThat(result).contains(entry);
	}

	@Test
	void consume_unknownTokenReturnsEmpty() {
		when(valueOps.getAndDelete(KEY)).thenReturn(null);

		assertThat(store.consume("tok-1")).isEmpty();
	}

	@Test
	void consume_expiredEntryReturnsEmpty() {
		when(valueOps.getAndDelete(KEY)).thenReturn(entry(Instant.now().minusSeconds(1)));

		assertThat(store.consume("tok-1")).isEmpty();
	}

	@Test
	void revoke_deletesNamespacedKey() {
		store.revoke("tok-1");

		verify(redis).delete(KEY);
	}

	private static RefreshTokenEntry entry(Instant expiresAt) {
		return new RefreshTokenEntry("user-1", "user1@example.com", "User One", "ROLE_USER", expiresAt);
	}
}
