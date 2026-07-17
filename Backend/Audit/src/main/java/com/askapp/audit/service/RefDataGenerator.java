package com.askapp.audit.service;

import com.askapp.audit.model.SecurityMaster;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Produces synthetic security-master records shaped like real reference data. Deterministic by
 * index: instrument {@code n} always has the same identifiers and attributes, so ingestion is
 * idempotent (re-running a range writes nothing new) and tests can assert exact values.
 *
 * <p>This inline generator is the ingestion source for the read-side foundation; the Spring Batch
 * job (next increment) reads from the same generator, just chunked and restartable.
 */
@Component
public class RefDataGenerator {

	private static final String[] ASSET_CLASSES = {"EQUITY", "BOND", "ETF", "FX"};
	private static final String[] CURRENCIES = {"USD", "EUR", "GBP", "JPY", "CHF"};

	/** Generate the first {@code count} synthetic securities (indexes {@code 0..count-1}). */
	public List<SecurityMaster> generate(int count) {
		return generate(0, count);
	}

	/**
	 * Generate {@code count} synthetic securities starting at {@code startIndex} (indexes
	 * {@code startIndex..startIndex+count-1}). The daily incremental batch passes the current row
	 * count as {@code startIndex} so each run appends a fresh, non-overlapping range rather than
	 * re-generating index 0 (which the dedupe step would then discard as already present).
	 */
	public List<SecurityMaster> generate(int startIndex, int count) {
		LocalDate asOf = LocalDate.now();
		List<SecurityMaster> records = new ArrayList<>(Math.max(0, count));
		for (int i = 0; i < count; i++) {
			records.add(at(startIndex + i, asOf));
		}
		return records;
	}

	private SecurityMaster at(int index, LocalDate asOf) {
		String instrumentId = String.format("SEC-%06d", index);
		String assetClass = ASSET_CLASSES[index % ASSET_CLASSES.length];
		String currency = CURRENCIES[index % CURRENCIES.length];
		// Deterministic pseudo-identifiers — not checksum-valid, but the right shape/length.
		String isin = String.format("US%010d", index);
		String cusip = String.format("CUSIP%04d", index % 10000);
		String sedol = String.format("SED%04d", index % 10000);
		String name = "Synthetic " + assetClass + " " + instrumentId;
		BigDecimal price = BigDecimal.valueOf(10.0 + (index % 1000) * 0.25).setScale(2, RoundingMode.HALF_UP);
		return new SecurityMaster(instrumentId, isin, cusip, sedol, name, assetClass, currency, price, asOf);
	}
}
