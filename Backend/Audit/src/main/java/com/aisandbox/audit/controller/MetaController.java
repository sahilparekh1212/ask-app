package com.aisandbox.audit.controller;

import com.aisandbox.audit.dto.FeaturesResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Runtime capability flags for the SPA. The same production-built bundle runs against both a
 * DEV backend (the local docker stack) and a PROD backend (the deployed VM), so the client
 * can't tell from its own build whether a profile-gated feature exists — it asks here.
 *
 * <p>Currently one flag: whether the demo-data generator ({@link DemoDataController}, gated to
 * LOCAL/DEV) is present, so the dashboard can hide its "Add demo logs" button in deployments
 * where that endpoint would 404. Deriving the flag from the bean's own presence keeps a single
 * source of truth — the {@code @Profile} on the controller — instead of duplicating the
 * profile list here.
 */
@RestController
@RequestMapping("/api/v1/meta")
@Tag(name = "Meta", description = "Runtime capability flags for the client")
public class MetaController {

	private final ObjectProvider<DemoDataController> demoController;

	public MetaController(ObjectProvider<DemoDataController> demoController) {
		this.demoController = demoController;
	}

	@GetMapping("/features")
	@Operation(summary = "Capability flags the client uses to adapt its UI to this deployment")
	public FeaturesResponse features() {
		return new FeaturesResponse(demoController.getIfAvailable() != null);
	}

}
