package com.askapp.auth.service;

import java.time.Instant;

/**
 * A single issued refresh token's stored state: the claims to restore onto the next access token
 * minted from it, plus its absolute expiry. Persisted by a {@link RefreshTokenStore} (in-memory or
 * Redis) and returned on consumption.
 */
public record RefreshTokenEntry(String userId, String email, String name, String role, Instant expiresAt) {}
