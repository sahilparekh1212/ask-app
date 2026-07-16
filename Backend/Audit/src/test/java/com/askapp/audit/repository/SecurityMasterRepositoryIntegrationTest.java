package com.askapp.audit.repository;

import com.askapp.audit.dto.SecurityFilter;
import com.askapp.audit.model.SecurityMaster;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises {@link SecurityMasterSpecifications} against H2 so its criteria predicates actually
 * run (mock tests never invoke the Specification lambda), and confirms the entity mapping + the
 * Liquibase {@code security_master} table line up.
 */
@DataJpaTest
class SecurityMasterRepositoryIntegrationTest {

	@Autowired
	private SecurityMasterRepository repository;

	@BeforeEach
	void seed() {
		repository.deleteAll();
		repository.save(sec("SEC-000000", "EQUITY", "USD", "Apple Inc"));
		repository.save(sec("SEC-000001", "BOND", "EUR", "Bund 10Y"));
		repository.save(sec("SEC-000002", "EQUITY", "GBP", "Vodafone Group"));
		repository.flush();
	}

	@Test
	void matching_filtersByAssetClass() {
		Page<SecurityMaster> page = repository.findAll(
			SecurityMasterSpecifications.matching(new SecurityFilter("EQUITY", null, null)), PageRequest.of(0, 20));

		assertThat(page.getContent()).extracting(SecurityMaster::getInstrumentId)
			.containsExactlyInAnyOrder("SEC-000000", "SEC-000002");
	}

	@Test
	void matching_filtersByCurrency() {
		Page<SecurityMaster> page = repository.findAll(
			SecurityMasterSpecifications.matching(new SecurityFilter(null, "EUR", null)), PageRequest.of(0, 20));

		assertThat(page.getContent()).singleElement()
			.extracting(SecurityMaster::getInstrumentId).isEqualTo("SEC-000001");
	}

	@Test
	void matching_filtersByNameSubstringCaseInsensitively() {
		Page<SecurityMaster> page = repository.findAll(
			SecurityMasterSpecifications.matching(new SecurityFilter(null, null, "vodafone")), PageRequest.of(0, 20));

		assertThat(page.getContent()).singleElement()
			.extracting(SecurityMaster::getName).isEqualTo("Vodafone Group");
	}

	@Test
	void matching_withNoConstraintsReturnsEveryRow() {
		Page<SecurityMaster> page = repository.findAll(
			SecurityMasterSpecifications.matching(new SecurityFilter(null, null, null)), PageRequest.of(0, 20));

		assertThat(page.getTotalElements()).isEqualTo(3);
	}

	@Test
	void findAndExistsByInstrumentId() {
		assertThat(repository.findByInstrumentId("SEC-000001")).isPresent();
		assertThat(repository.findByInstrumentId("SEC-999999")).isEmpty();
		assertThat(repository.existsByInstrumentId("SEC-000000")).isTrue();
		assertThat(repository.existsByInstrumentId("SEC-nope")).isFalse();
	}

	private SecurityMaster sec(String id, String assetClass, String currency, String name) {
		return new SecurityMaster(id, "US" + id, "C" + id, "S" + id, name, assetClass, currency,
			new BigDecimal("10.00"), LocalDate.now());
	}
}
