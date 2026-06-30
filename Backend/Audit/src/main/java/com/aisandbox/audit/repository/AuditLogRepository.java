package com.aisandbox.audit.repository;

import com.aisandbox.audit.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface AuditLogRepository
		extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

	/** Active (not soft-deleted) audit logs. */
	List<AuditLog> findByDeletedFalse();
}
