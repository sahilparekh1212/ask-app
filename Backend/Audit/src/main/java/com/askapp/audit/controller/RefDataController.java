package com.askapp.audit.controller;

import com.askapp.audit.dto.IngestResponse;
import com.askapp.audit.dto.PagedResponse;
import com.askapp.audit.dto.SecurityFilter;
import com.askapp.audit.model.SecurityMaster;
import com.askapp.audit.service.RefDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Reference-data (security master) API: a batch-loaded, read-heavy dataset styled as financial
 * reference data. Ingestion is a synthetic bulk load (a real Spring Batch job replaces the inline
 * loader in a follow-up); reads are paginated, filterable, and support field projection.
 */
@RestController
@RequestMapping("/api/v1/refdata")
@Tag(name = "Reference Data", description = "Security-master reference data: bulk ingestion + "
	+ "paginated, filterable, field-projected reads.")
public class RefDataController {

	private static final Logger log = LoggerFactory.getLogger(RefDataController.class);

	/** Guardrail on a single ingestion run so a typo can't try to generate an unbounded batch. */
	private static final int MAX_INGEST = 100_000;

	private final RefDataService refDataService;

	public RefDataController(RefDataService refDataService) {
		this.refDataService = refDataService;
	}

	@PostMapping("/ingest")
	@ResponseStatus(HttpStatus.ACCEPTED)
	@PreAuthorize("hasRole('ADMIN')")
	@Operation(summary = "Bulk-ingest N synthetic security-master records (admin only). Idempotent: "
		+ "records whose instrument id already exists are skipped.")
	public IngestResponse ingest(@RequestParam(defaultValue = "1000") int count) {
		if (count < 1 || count > MAX_INGEST) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
				"count must be between 1 and " + MAX_INGEST);
		}
		log.info("Ingesting {} security-master records", count);
		IngestResponse response = refDataService.ingest(count);
		log.info("Ingested {} of {} requested security-master records", response.ingested(), count);
		return response;
	}

	@GetMapping("/securities")
	@Operation(summary = "List securities (filters: assetClass, currency, name substring). Pass "
		+ "fields=a,b,c to project only those columns; unknown field names are ignored.")
	public PagedResponse<Object> listSecurities(
			@RequestParam(required = false) String assetClass,
			@RequestParam(required = false) String currency,
			@RequestParam(required = false) String name,
			@RequestParam(required = false) List<String> fields,
			@PageableDefault(size = 20) Pageable pageable) {
		SecurityFilter filter = new SecurityFilter(assetClass, currency, name);
		log.info("Listing securities filter={} fields={} page={}", filter, fields, pageable.getPageNumber());
		Page<SecurityMaster> page = refDataService.search(filter, pageable);
		Set<String> selected = selectedFields(fields);
		Page<Object> content = selected.isEmpty()
			? page.map(s -> s)
			: page.map(s -> refDataService.project(s, selected));
		return PagedResponse.from(content);
	}

	@GetMapping("/securities/{instrumentId}")
	@Operation(summary = "Get a single security by its instrument id")
	public SecurityMaster getSecurity(@PathVariable String instrumentId) {
		log.info("Fetching security {}", instrumentId);
		return refDataService.findByInstrumentId(instrumentId);
	}

	/** Intersect the requested field names with the whitelist, keeping request order. */
	private Set<String> selectedFields(List<String> fields) {
		if (fields == null || fields.isEmpty()) {
			return Set.of();
		}
		Set<String> allowed = RefDataService.projectableFields();
		Set<String> selected = new LinkedHashSet<>();
		for (String field : fields) {
			if (allowed.contains(field)) {
				selected.add(field);
			}
		}
		return selected;
	}

}
