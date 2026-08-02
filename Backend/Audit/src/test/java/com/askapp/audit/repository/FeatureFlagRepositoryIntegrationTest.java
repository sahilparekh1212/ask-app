package com.askapp.audit.repository;

import com.askapp.audit.model.FeatureFlag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs against H2 with the real Liquibase schema, so it both confirms the {@link FeatureFlag}
 * mapping lines up with the {@code 009-create-feature-flags} table and proves the four flags are
 * seeded ON. {@code @DataJpaTest} is transactional and rolls back, so the duplicate-key probe
 * doesn't leak.
 */
@DataJpaTest
class FeatureFlagRepositoryIntegrationTest {

	@Autowired
	private FeatureFlagRepository repository;

	@Test
	void seedsTheFourMajorFeaturesAllEnabled() {
		List<FeatureFlag> flags = repository.findAllByOrderByFlagKeyAsc();

		assertThat(flags).extracting(FeatureFlag::getFlagKey)
			.containsExactly("chat", "hints", "observability", "voice");
		assertThat(flags).allSatisfy(f -> {
			assertThat(f.isEnabled()).isTrue();
			assertThat(f.getDescription()).isNotBlank();
		});
	}

	@Test
	void flagKeyIsUnique() {
		assertThatThrownBy(() -> repository.saveAndFlush(new FeatureFlag("chat", true, "duplicate")))
			.isInstanceOf(DataIntegrityViolationException.class);
	}
}
