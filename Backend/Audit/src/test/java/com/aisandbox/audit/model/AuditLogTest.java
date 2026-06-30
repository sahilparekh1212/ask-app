package com.aisandbox.audit.model;

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
	void settersMutateEveryField() {
		AuditLog log = new AuditLog("User", "CREATE", "details");

		log.setEntityType("Order");
		log.setAction("UPDATE");
		log.setDetails("changed");
		log.setDeleted(true);

		assertThat(log.getEntityType()).isEqualTo("Order");
		assertThat(log.getAction()).isEqualTo("UPDATE");
		assertThat(log.getDetails()).isEqualTo("changed");
		assertThat(log.isDeleted()).isTrue();
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
