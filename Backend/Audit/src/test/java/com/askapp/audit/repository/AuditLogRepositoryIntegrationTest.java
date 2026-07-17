package com.askapp.audit.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the retention purge's bulk {@code DELETE} against H2 — both validating the {@code @Query}
 * JPQL and confirming the range spans soft-deleted rows. Rows are inserted with explicit
 * {@code created_at} values via native SQL so the test controls the age directly (independent of the
 * JPA auditing listener). Also implicitly proves changesets 005 (the index) and 006 (the view) apply.
 */
@DataJpaTest
class AuditLogRepositoryIntegrationTest {

	@Autowired
	private AuditLogRepository repository;

	@Autowired
	private TestEntityManager entityManager;

	@Test
	void deleteByCreatedAtBefore_removesOldRowsIncludingSoftDeleted_keepsRecent() {
		Instant now = Instant.now();
		insert("User", "LOGIN", false, now.minus(Duration.ofDays(120))); // old, active
		insert("User", "LOGOUT", true, now.minus(Duration.ofDays(100))); // old, soft-deleted
		insert("User", "LOGIN", false, now.minus(Duration.ofDays(10)));  // recent, active

		int removed = repository.deleteByCreatedAtBefore(now.minus(Duration.ofDays(90)));
		entityManager.clear(); // bulk delete bypasses the persistence context

		assertThat(removed).isEqualTo(2);
		assertThat(repository.count()).isEqualTo(1);
	}

	private void insert(String entityType, String action, boolean deleted, Instant createdAt) {
		entityManager.getEntityManager()
			.createNativeQuery("INSERT INTO audit_logs (entity_type, action, deleted, created_at) "
				+ "VALUES (?, ?, ?, ?)")
			.setParameter(1, entityType)
			.setParameter(2, action)
			.setParameter(3, deleted)
			.setParameter(4, Timestamp.from(createdAt))
			.executeUpdate();
	}
}
