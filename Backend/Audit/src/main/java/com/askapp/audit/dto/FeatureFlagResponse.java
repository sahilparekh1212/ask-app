package com.askapp.audit.dto;

import com.askapp.audit.model.FeatureFlag;

/**
 * One UI feature flag as served to the SPA. The endpoint returns a list of these, so adding or
 * removing flag <em>rows</em> never changes the OpenAPI schema — the public contract is the fixed
 * {@code {key, enabled, description}} shape, keeping the api-contract check additive.
 *
 * @param key         the stable flag key the client gates on (e.g. {@code chat})
 * @param enabled     whether the feature should be shown
 * @param description human-readable note (for docs/tooltips); may be null
 */
public record FeatureFlagResponse(String key, boolean enabled, String description) {

	public static FeatureFlagResponse from(FeatureFlag flag) {
		return new FeatureFlagResponse(flag.getFlagKey(), flag.isEnabled(), flag.getDescription());
	}
}
