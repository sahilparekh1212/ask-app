package com.aisandbox.audit.controller;

import com.aisandbox.audit.dto.DemoDataRequest;
import com.aisandbox.audit.dto.DemoDataResponse;
import com.aisandbox.audit.model.AuditLog;
import com.aisandbox.audit.ratelimit.TransactionalRequestExecutor;
import com.aisandbox.audit.repository.AuditLogRepository;
import com.aisandbox.audit.service.DemoDataGenerator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Bulk-inserts generated demo rows so a reviewer can populate the dashboard with one click.
 * LOCAL/DEV only (same gate as {@code DemoDataSeeder}) — in SIT/UAT/PROD this controller does
 * not exist and the route 404s, because dummy rows have no business in a real audit trail.
 */
@RestController
@Profile({"LOCAL", "DEV"})
@RequestMapping("/api/v1/audit-logs/demo")
@Tag(name = "Demo data", description = "Generate demo audit rows (LOCAL/DEV only)")
public class DemoDataController {

	private static final Logger log = LoggerFactory.getLogger(DemoDataController.class);

	private final AuditLogRepository repository;
	private final DemoDataGenerator generator;
	// Same supersede semantics as the other mutations on this API (see AuditLogController).
	private final TransactionalRequestExecutor txExecutor;

	public DemoDataController(AuditLogRepository repository, DemoDataGenerator generator,
			TransactionalRequestExecutor txExecutor) {
		this.repository = repository;
		this.generator = generator;
		this.txExecutor = txExecutor;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Generate and insert demo audit rows (count 1..500)")
	public DemoDataResponse generate(@Valid @RequestBody DemoDataRequest request) {
		List<AuditLog> saved = txExecutor.run(() -> repository.saveAll(generator.generate(request.count())));
		log.info("Generated {} demo audit log rows", saved.size());
		return new DemoDataResponse(saved.size());
	}

}
