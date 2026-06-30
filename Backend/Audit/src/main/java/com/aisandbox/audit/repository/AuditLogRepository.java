package com.aisandbox.audit.repository;

import com.aisandbox.audit.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface AuditLogRepository
		extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

	/** Active (not soft-deleted) audit logs. */
	List<AuditLog> findByDeletedFalse();

	/** True if an event with this id was already consumed — backs idempotent Kafka consumption. */
	boolean existsByEventId(String eventId);
}
