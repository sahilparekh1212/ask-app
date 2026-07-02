package com.aisandbox.auth.model;

/** The two roles this app knows about, in the exact form they're carried as a JWT claim value. */
public final class Roles {

	public static final String ROLE_USER = "ROLE_USER";
	public static final String ROLE_ADMIN = "ROLE_ADMIN";

	/** Validated by {@link LoginRequest#role()}'s {@code @Pattern}, so any non-blank value here is one of the two. */
	public static final String VALID_ROLES_PATTERN = "ROLE_USER|ROLE_ADMIN";

	private Roles() {
	}

}
