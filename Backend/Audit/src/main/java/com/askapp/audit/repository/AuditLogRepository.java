package com.askapp.audit.repository;

import com.askapp.audit.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface AuditLogRepository
		extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

	/** Active (not soft-deleted) audit logs. */
	List<AuditLog> findByDeletedFalse();

	/** True if an event with this id was already consumed — backs idempotent Kafka consumption. */
	boolean existsByEventId(String eventId);

	/**
	 * Hard-delete every audit row created before {@code cutoff} (both active and soft-deleted) —
	 * the retention purge. A single bulk {@code DELETE} rather than load-then-remove, so it never
	 * materializes the doomed rows; rides {@code idx_audit_created_at}. Returns the rows removed.
	 */
	@Modifying
	@Query("delete from AuditLog a where a.createdAt < :cutoff")
	int deleteByCreatedAtBefore(@Param("cutoff") Instant cutoff);
}
