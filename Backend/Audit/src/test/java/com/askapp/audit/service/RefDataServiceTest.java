package com.askapp.audit.service;

import com.askapp.audit.dto.SecurityFilter;
import com.askapp.audit.exception.ResourceNotFoundException;
import com.askapp.audit.model.SecurityMaster;
import com.askapp.audit.repository.SecurityMasterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RefDataServiceTest {

	private SecurityMasterRepository repository;
	private RefDataService service;

	@BeforeEach
	void setUp() {
		repository = mock(SecurityMasterRepository.class);
		service = new RefDataService(repository);
	}

	@Test
	void search_delegatesToRepositoryWithASpecification() {
		Pageable pageable = PageRequest.of(0, 20);
		Page<SecurityMaster> page = new PageImpl<>(List.of(sample()));
		when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

		Page<SecurityMaster> result = service.search(new SecurityFilter("EQUITY", null, null), pageable);

		assertThat(result).isSameAs(page);
	}

	@Test
	void findByInstrumentId_returnsWhenPresent() {
		SecurityMaster sm = sample();
		when(repository.findByInstrumentId("SEC-000000")).thenReturn(Optional.of(sm));

		assertThat(service.findByInstrumentId("SEC-000000")).isSameAs(sm);
	}

	@Test
	void findByInstrumentId_throwsWhenMissing() {
		when(repository.findByInstrumentId("SEC-999999")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.findByInstrumentId("SEC-999999"))
			.isInstanceOf(ResourceNotFoundException.class)
			.hasMessageContaining("SEC-999999");
	}

	@Test
	void project_selectsOnlyRequestedFieldsInCanonicalOrder() {
		Map<String, Object> projected = service.project(sample(), Set.of("isin", "instrumentId"));

		assertThat(projected).containsOnlyKeys("instrumentId", "isin");
		assertThat(projected.keySet()).containsExactly("instrumentId", "isin");
		assertThat(projected.get("instrumentId")).isEqualTo("SEC-000000");
	}

	@Test
	void project_emptySelectionReturnsAllFields() {
		Map<String, Object> projected = service.project(sample(), Set.of());

		assertThat(projected.keySet()).containsExactlyElementsOf(RefDataService.projectableFields());
	}

	@Test
	void projectableFields_exposesTheWhitelist() {
		assertThat(RefDataService.projectableFields())
			.contains("instrumentId", "isin", "cusip", "sedol", "name", "assetClass", "currency",
				"price", "asOfDate", "id");
	}

	private SecurityMaster sample() {
		return new SecurityMaster("SEC-000000", "US0000000000", "CUSIP0000", "SED0000",
			"Synthetic EQUITY SEC-000000", "EQUITY", "USD", new BigDecimal("10.00"), LocalDate.now());
	}
}
