package com.askapp.audit.controller;

import com.askapp.audit.dto.AuditLogFilter;
import com.askapp.audit.dto.PagedResponse;
import com.askapp.audit.model.AuditLog;
import com.askapp.audit.service.AuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * v2 of the Audit Log API — a sample of URI-based API versioning.
 *
 * <p>Only the <em>list</em> contract evolved between versions: v1's
 * {@code GET /api/v1/audit-logs} returns a flat {@code List<AuditLog>}, whereas v2 returns a
 * paginated {@link PagedResponse} — the sustainable default for a table that grows without bound.
 * v1 stays in place so existing clients keep working; new clients adopt v2. Every other operation
 * (get-by-id, create, soft-delete, search, stats) was unchanged and remains on v1, so it is not
 * duplicated here — clients mix versions per endpoint, which is the point of URI versioning.
 */
@RestController
@RequestMapping("/api/v2/audit-logs")
@Tag(name = "Audit Logs v2", description = "Versioned sample — paginated listing (v1 keeps the full CRUD surface)")
public class AuditLogV2Controller {

	private static final Logger log = LoggerFactory.getLogger(AuditLogV2Controller.class);

	private final AuditLogService auditLogService;

	public AuditLogV2Controller(AuditLogService auditLogService) {
		this.auditLogService = auditLogService;
	}

	@GetMapping
	@Operation(summary = "List audit logs — v2 returns a paginated response instead of v1's flat list")
	public PagedResponse<AuditLog> findAll(
			@RequestParam(defaultValue = "false") boolean includeDeleted,
			@PageableDefault(size = 20) Pageable pageable) {
		log.info("Fetching audit logs (v2) includeDeleted={} page={} size={}", includeDeleted,
			pageable.getPageNumber(), pageable.getPageSize());
		AuditLogFilter filter = new AuditLogFilter(null, null, null, null, includeDeleted);
		return PagedResponse.from(auditLogService.search(filter, pageable));
	}

}
