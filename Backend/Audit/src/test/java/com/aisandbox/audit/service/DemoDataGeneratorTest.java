package com.aisandbox.audit.service;

import com.aisandbox.audit.model.AuditLog;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DemoDataGeneratorTest {

	private final DemoDataGenerator generator = new DemoDataGenerator();

	@Test
	void generate_returnsExactlyTheRequestedNumberOfRows() {
		assertThat(generator.generate(1)).hasSize(1);
		assertThat(generator.generate(50)).hasSize(50);
	}

	@Test
	void generate_producesFullyPopulatedRowsFromTheKnownVocabulary() {
		Set<String> entityTypes = Set.of("User", "Order", "Payment", "Inventory", "Report");
		Set<String> actions = Set.of("LOGIN", "TOKEN_REFRESH", "LOGOUT", "CREATE", "UPDATE", "DELETE");

		List<AuditLog> rows = generator.generate(100);

		assertThat(rows).allSatisfy(row -> {
			assertThat(row.getEntityType()).isIn(entityTypes);
			assertThat(row.getAction()).isIn(actions);
			assertThat(row.getDetails()).isNotBlank().doesNotContain("%d");
			assertThat(row.getEventId()).isNull(); // generated rows are REST-style, not Kafka-sourced
			assertThat(row.isDeleted()).isFalse();
		});
	}

	@Test
	void generate_variesTheDetailsSoSearchDemosAreInteresting() {
		List<AuditLog> rows = generator.generate(100);

		long distinctDetails = rows.stream().map(AuditLog::getDetails).distinct().count();
		// 100 draws over 12 templates × ~9000 ids collide occasionally; "well above one
		// per template" is the property that matters for a details-search demo.
		assertThat(distinctDetails).isGreaterThan(20);
	}

}
