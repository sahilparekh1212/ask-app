package com.aisandbox.audit.controller;

import com.aisandbox.audit.dto.AuditLogFilter;
import com.aisandbox.audit.dto.AuditLogStats;
import com.aisandbox.audit.dto.AuditLogTimeBucket;
import com.aisandbox.audit.dto.PagedResponse;
import com.aisandbox.audit.dto.TimelineInterval;
import com.aisandbox.audit.exception.ResourceNotFoundException;
import com.aisandbox.audit.model.AuditLog;
import com.aisandbox.audit.ratelimit.TransactionalRequestExecutor;
import com.aisandbox.audit.repository.AuditLogRepository;
import com.aisandbox.audit.service.AuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/audit-logs")
@Tag(name = "Audit Logs v1", description = "Audit log operations (immutable — no update). "
	+ "URI-versioned: see AuditLogV2Controller for the v2 paginated listing.")
public class AuditLogController {

	private static final Logger log = LoggerFactory.getLogger(AuditLogController.class);

	private final AuditLogRepository auditLogRepository;
	private final AuditLogService auditLogService;
	// Wraps mutations so a superseding request rolls the work back transactionally.
	private final TransactionalRequestExecutor txExecutor;

	public AuditLogController(AuditLogRepository auditLogRepository, AuditLogService auditLogService,
			TransactionalRequestExecutor txExecutor) {
		this.auditLogRepository = auditLogRepository;
		this.auditLogService = auditLogService;
		this.txExecutor = txExecutor;
	}

	@GetMapping
	@Operation(summary = "List audit logs (excludes soft-deleted unless includeDeleted=true)")
	public List<AuditLog> findAll(@RequestParam(defaultValue = "false") boolean includeDeleted) {
		log.info("Fetching audit logs includeDeleted={}", includeDeleted);
		return includeDeleted ? auditLogRepository.findAll() : auditLogRepository.findByDeletedFalse();
	}

	@GetMapping("/search")
	@Operation(summary = "Paginated, filtered, sorted search over audit logs "
		+ "(filters: entityType, action, details substring, createdAt range; "
		+ "sortable: createdAt, entityType, action, id)")
	public PagedResponse<AuditLog> search(
			@RequestParam(required = false) String entityType,
			@RequestParam(required = false) String action,
			@RequestParam(required = false) String details,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
			@RequestParam(defaultValue = "false") boolean includeDeleted,
			@PageableDefault(size = 20) Pageable pageable) {
		AuditLogFilter filter = new AuditLogFilter(entityType, action, details, from, to, includeDeleted);
		log.info("Searching audit logs filter={} page={} size={}", filter, pageable.getPageNumber(),
			pageable.getPageSize());
		return PagedResponse.from(auditLogService.search(filter, pageable));
	}

	@GetMapping("/stats")
	@Operation(summary = "Aggregated counts (total, by action, by entityType) over the same filters as /search")
	public AuditLogStats stats(
			@RequestParam(required = false) String entityType,
			@RequestParam(required = false) String action,
			@RequestParam(required = false) String details,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
			@RequestParam(defaultValue = "false") boolean includeDeleted) {
		AuditLogFilter filter = new AuditLogFilter(entityType, action, details, from, to, includeDeleted);
		log.info("Aggregating audit logs filter={}", filter);
		return auditLogService.aggregate(filter);
	}

	@GetMapping("/stats/timeline")
	@Operation(summary = "Events-over-time buckets (hour or day granularity) over the same filters as /search. "
		+ "Empty buckets are omitted; bucket boundaries follow the server session's time zone (UTC in prod).")
	public List<AuditLogTimeBucket> timeline(
			@RequestParam(required = false) String entityType,
			@RequestParam(required = false) String action,
			@RequestParam(required = false) String details,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
			@RequestParam(defaultValue = "false") boolean includeDeleted,
			@RequestParam(defaultValue = "hour") TimelineInterval interval) {
		AuditLogFilter filter = new AuditLogFilter(entityType, action, details, from, to, includeDeleted);
		log.info("Timeline aggregation filter={} interval={}", filter, interval);
		return auditLogService.timeline(filter, interval);
	}

	@GetMapping("/{id}")
	@Operation(summary = "Get audit log by ID")
	public AuditLog findById(@PathVariable Long id) {
		log.info("Fetching audit log id={}", id);
		return auditLogRepository.findById(id)
			.orElseThrow(() -> new ResourceNotFoundException("Audit log not found: " + id));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Create a new audit log entry")
	public AuditLog create(@RequestBody AuditLog auditLog) {
		AuditLog saved = txExecutor.run(() -> auditLogRepository.save(auditLog));
		log.info("Created audit log id={} action={}", saved.getId(), saved.getAction());
		return saved;
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize("hasRole('ADMIN')")
	@Operation(summary = "Delete an audit log entry (admin only)")
	public void delete(@PathVariable Long id) {
		txExecutor.run(() -> {
			AuditLog auditLog = auditLogRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Audit log not found: " + id));
			auditLog.markDeleted();
			auditLogRepository.save(auditLog);
			return null;
		});
		log.info("Soft-deleted audit log id={}", id);
	}

	@GetMapping("/health")
	@Operation(summary = "Health check")
	public ResponseEntity<String> health() {
		return ResponseEntity.ok("audit-service OK");
	}

}
