package com.aisandbox.audit.repository;

import com.aisandbox.audit.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

	/** Active (not soft-deleted) audit logs. */
	List<AuditLog> findByDeletedFalse();
}
