package com.aisandbox.auth.model;

import jakarta.validation.constraints.Pattern;

/**
 * Credentials for the demo username/password login ({@code POST /auth/login}).
 *
 * <p>This backs the recruiter demo account (default username/password both {@code demo}) so the
 * app can be tried without configuring real Google OAuth. It issues the exact same JWTs as the
 * OAuth flow — see {@code AuthController}.
 *
 * @param role Optional; {@code ROLE_USER} (the default) or {@code ROLE_ADMIN}, so the demo
 *             account can be used to test admin-gated endpoints (e.g. deleting audit logs)
 *             without a real identity provider modeling roles.
 */
public record LoginRequest(
	String username,
	String password,
	@Pattern(regexp = Roles.VALID_ROLES_PATTERN, message = "must be ROLE_USER or ROLE_ADMIN") String role
) {
}
