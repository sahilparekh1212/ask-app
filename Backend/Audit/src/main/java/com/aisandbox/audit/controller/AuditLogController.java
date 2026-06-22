package com.aisandbox.audit.controller;

import com.aisandbox.audit.model.AuditLog;
import com.aisandbox.audit.repository.AuditLogRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/audit-logs")
public class AuditLogController {

	private final AuditLogRepository auditLogRepository;

	public AuditLogController(AuditLogRepository auditLogRepository) {
		this.auditLogRepository = auditLogRepository;
	}

	@GetMapping
	public List<AuditLog> findAll() {
		return auditLogRepository.findAll();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public AuditLog create(@RequestBody AuditLog auditLog) {
		return auditLogRepository.save(auditLog);
	}

}
