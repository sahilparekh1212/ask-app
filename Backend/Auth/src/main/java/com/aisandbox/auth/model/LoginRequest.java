package com.aisandbox.auth.model;

/**
 * Credentials for the demo username/password login ({@code POST /auth/login}).
 *
 * <p>This backs the recruiter demo account (default username/password both {@code demo}) so the
 * app can be tried without configuring real Google OAuth. It issues the exact same JWTs as the
 * OAuth flow — see {@code AuthController}.
 */
public record LoginRequest(String username, String password) {
}
