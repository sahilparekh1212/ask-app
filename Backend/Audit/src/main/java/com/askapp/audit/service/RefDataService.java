package com.askapp.audit.service;

import com.askapp.audit.dto.SecurityFilter;
import com.askapp.audit.exception.ResourceNotFoundException;
import com.askapp.audit.model.SecurityMaster;
import com.askapp.audit.repository.SecurityMasterRepository;
import com.askapp.audit.repository.SecurityMasterSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Queries security-master reference data. Ingestion is handled by the Spring Batch job (see
 * {@code RefDataBatchConfig} / {@code RefDataIngestService}); this service is the read side. Reads
 * support field projection so a caller can ask for only the columns it needs — the
 * payload-optimization angle of a read-heavy service.
 */
@Service
public class RefDataService {

	/**
	 * Whitelisted projection fields → extractor. A {@code fields=} request is intersected with
	 * this set (unknown names are ignored), so projection can never reflect arbitrary properties.
	 */
	private static final Map<String, Function<SecurityMaster, Object>> FIELDS = buildFields();

	private final SecurityMasterRepository repository;

	public RefDataService(SecurityMasterRepository repository) {
		this.repository = repository;
	}

	public Page<SecurityMaster> search(SecurityFilter filter, Pageable pageable) {
		return repository.findAll(SecurityMasterSpecifications.matching(filter), pageable);
	}

	public SecurityMaster findByInstrumentId(String instrumentId) {
		return repository.findByInstrumentId(instrumentId)
			.orElseThrow(() -> new ResourceNotFoundException("Security not found: " + instrumentId));
	}

	/** The projectable field names (used by the controller to validate/parse {@code fields=}). */
	public static Set<String> projectableFields() {
		return FIELDS.keySet();
	}

	/**
	 * Project a record down to the requested fields, preserving the canonical field order.
	 * Names outside the whitelist are ignored; an empty/blank selection yields all fields, so the
	 * caller never gets an empty object back.
	 */
	public Map<String, Object> project(SecurityMaster security, Set<String> requested) {
		Map<String, Object> out = new LinkedHashMap<>();
		for (Map.Entry<String, Function<SecurityMaster, Object>> field : FIELDS.entrySet()) {
			if (requested.isEmpty() || requested.contains(field.getKey())) {
				out.put(field.getKey(), field.getValue().apply(security));
			}
		}
		return out;
	}

	private static Map<String, Function<SecurityMaster, Object>> buildFields() {
		Map<String, Function<SecurityMaster, Object>> fields = new LinkedHashMap<>();
		fields.put("id", SecurityMaster::getId);
		fields.put("instrumentId", SecurityMaster::getInstrumentId);
		fields.put("isin", SecurityMaster::getIsin);
		fields.put("cusip", SecurityMaster::getCusip);
		fields.put("sedol", SecurityMaster::getSedol);
		fields.put("name", SecurityMaster::getName);
		fields.put("assetClass", SecurityMaster::getAssetClass);
		fields.put("currency", SecurityMaster::getCurrency);
		fields.put("price", SecurityMaster::getPrice);
		fields.put("asOfDate", SecurityMaster::getAsOfDate);
		return fields;
	}
}
