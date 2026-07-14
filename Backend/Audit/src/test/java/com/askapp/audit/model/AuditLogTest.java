package com.askapp.audit.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuditLogTest {

	@Test
	void constructorPopulatesTheBusinessFields() {
		AuditLog log = new AuditLog("User", "CREATE", "details");

		assertThat(log.getEntityType()).isEqualTo("User");
		assertThat(log.getAction()).isEqualTo("CREATE");
		assertThat(log.getDetails()).isEqualTo("details");
		assertThat(log.isDeleted()).isFalse();
		assertThat(log.getId()).isNull();
	}

	@Test
	void markDeletedIsAOneWayTransition() {
		AuditLog log = new AuditLog("User", "CREATE", "details");

		assertThat(log.isDeleted()).isFalse();
		log.markDeleted();

		assertThat(log.isDeleted()).isTrue();
	}

	@Test
	void fourArgConstructorStampsTheSourceEventId() {
		AuditLog log = new AuditLog("User", "CREATE", "details", "evt-9");

		assertThat(log.getEventId()).isEqualTo("evt-9");
	}

	@Test
	void threeArgConstructorLeavesEventIdNull() {
		AuditLog log = new AuditLog("User", "CREATE", "details");

		assertThat(log.getEventId()).isNull();
	}

	@Test
	void inheritedAuditingFieldsAreNullUntilPersisted() {
		AuditLog log = new AuditLog("User", "CREATE", "details");

		assertThat(log.getCreatedAt()).isNull();
		assertThat(log.getUpdatedAt()).isNull();
		assertThat(log.getCreatedByRequestId()).isNull();
		assertThat(log.getUpdatedByRequestId()).isNull();
	}
}
