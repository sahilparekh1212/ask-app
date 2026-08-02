package com.askapp.audit.controller;

import com.askapp.audit.dto.FeatureFlagResponse;
import com.askapp.audit.service.FeatureFlagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Serves the UI feature flags the SPA reads at startup to decide which major features to show in
 * this deployment (chat, voice, hints, observability — see ADR-0015). Sits under the same
 * {@code /api/v1/meta} base as {@link MetaController}'s capability flags, but sourced from the
 * database rather than bean presence, so flags can be flipped without a redeploy.
 *
 * <p>Authenticated (parity with {@code /meta/features}); every gated feature lives behind login
 * anyway. Returns a list so adding/removing flags never changes the OpenAPI schema.
 */
@RestController
@RequestMapping("/api/v1/meta")
@Tag(name = "Meta", description = "Runtime capability flags for the client")
public class FeatureFlagController {

	private final FeatureFlagService service;

	public FeatureFlagController(FeatureFlagService service) {
		this.service = service;
	}

	@GetMapping("/flags")
	@Operation(summary = "UI feature flags controlling which SPA features are shown in this deployment")
	public List<FeatureFlagResponse> flags() {
		return service.list();
	}

}
