package com.askapp.audit.controller;

import com.askapp.audit.dto.IngestResponse;
import com.askapp.audit.dto.PagedResponse;
import com.askapp.audit.model.SecurityMaster;
import com.askapp.audit.service.RefDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefDataControllerTest {

	private RefDataService service;
	private RefDataController controller;

	@BeforeEach
	void setUp() {
		service = mock(RefDataService.class);
		controller = new RefDataController(service);
	}

	@Test
	void ingest_returnsServiceResult() {
		when(service.ingest(10)).thenReturn(new IngestResponse(10, 10, "generator"));

		IngestResponse response = controller.ingest(10);

		assertThat(response.ingested()).isEqualTo(10);
	}

	@Test
	void ingest_rejectsCountBelowOne() {
		assertThatThrownBy(() -> controller.ingest(0))
			.isInstanceOf(ResponseStatusException.class)
			.satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
	}

	@Test
	void ingest_rejectsCountAboveTheCap() {
		assertThatThrownBy(() -> controller.ingest(100_001))
			.isInstanceOf(ResponseStatusException.class);
		verify(service, never()).ingest(any(Integer.class));
	}

	@Test
	void listSecurities_withoutFields_returnsFullEntities() {
		SecurityMaster sm = sample();
		Pageable pageable = PageRequest.of(0, 20);
		when(service.search(any(), eq(pageable))).thenReturn(new PageImpl<>(List.of(sm)));

		PagedResponse<Object> result = controller.listSecurities(null, null, null, null, pageable);

		assertThat(result.content()).containsExactly(sm);
		verify(service, never()).project(any(), any());
	}

	@Test
	void listSecurities_withFields_returnsProjectedMaps() {
		SecurityMaster sm = sample();
		Pageable pageable = PageRequest.of(0, 20);
		when(service.search(any(), eq(pageable))).thenReturn(new PageImpl<>(List.of(sm)));
		Map<String, Object> projected = Map.of("instrumentId", "SEC-000000");
		when(service.project(any(), any())).thenReturn(projected);

		PagedResponse<Object> result =
			controller.listSecurities(null, null, null, List.of("instrumentId"), pageable);

		assertThat(result.content()).containsExactly(projected);
	}

	@Test
	void listSecurities_unknownFieldsAreIgnored_soFullEntitiesComeBack() {
		SecurityMaster sm = sample();
		Pageable pageable = PageRequest.of(0, 20);
		when(service.search(any(), eq(pageable))).thenReturn(new PageImpl<>(List.of(sm)));

		PagedResponse<Object> result =
			controller.listSecurities(null, null, null, List.of("bogus"), pageable);

		assertThat(result.content()).containsExactly(sm);
		verify(service, never()).project(any(), any());
	}

	@Test
	void getSecurity_returnsTheRecord() {
		SecurityMaster sm = sample();
		when(service.findByInstrumentId("SEC-000000")).thenReturn(sm);

		assertThat(controller.getSecurity("SEC-000000")).isSameAs(sm);
	}

	private SecurityMaster sample() {
		return new SecurityMaster("SEC-000000", "US0000000000", "CUSIP0000", "SED0000",
			"Synthetic EQUITY SEC-000000", "EQUITY", "USD", new BigDecimal("10.00"), LocalDate.now());
	}
}
