package com.askapp.audit.rag;

import com.askapp.audit.model.SecurityMaster;
import com.askapp.audit.repository.SecurityMasterRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns the security-master reference data into retrievable RAG chunks, so the assistant and the
 * MCP {@code search_knowledge} tool can answer questions about the dataset (what instruments exist,
 * their asset class / currency / price), not only about the repo's docs and code.
 *
 * <p>Chunking strategy is chosen for cheap, stable incremental indexing (chunk ids are content
 * hashes — see {@link DocChunk}):
 * <ul>
 *   <li>a single, deliberately <em>count-free</em> overview chunk describing the dataset's shape
 *       (identifiers, asset classes, currencies) — stable text, so it is embedded exactly once and
 *       never re-embedded as rows are added; and</li>
 *   <li>fixed-size windows of consecutive instruments rendered as compact rows. Because the data is
 *       append-only, every already-full window keeps the same rows and therefore the same content
 *       hash across re-indexes — only the trailing partial window (and any brand-new window) is
 *       embedded when the daily batch appends rows.</li>
 * </ul>
 *
 * <p>The reference data is <em>synthetic</em> (generated, non-PII), so exposing it through the
 * unauthenticated MCP endpoint carries no user data — see ADR-0010, whose "public data only"
 * boundary this stays within. Toggle with {@code rag.reference-data.enabled} (default true) and
 * size windows with {@code rag.reference-data.window-size} (default 20, ~1–2 KB per chunk).
 */
@Component
public class ReferenceDataChunker {

	private static final String OVERVIEW_SOURCE = "reference-data/overview";
	private static final String WINDOW_SOURCE_PREFIX = "reference-data/securities";

	private static final String OVERVIEW_CONTENT = """
		Security-master reference data (synthetic).

		This service carries a batch-loaded, read-heavy security-master dataset styled as financial
		reference data. Each record is a security identified by a stable instrument id (SEC-000000,
		SEC-000001, ...) and the standard cross-reference identifiers ISIN, CUSIP and SEDOL. Records
		also carry a descriptive name, an asset class (one of EQUITY, BOND, ETF, FX), an ISO 4217
		currency (USD, EUR, GBP, JPY, CHF), a price, and an as-of date. The data is generated
		(non-PII) but shaped to read as genuine reference data. It is bulk-loaded initially and grows
		by a small daily incremental batch, and is queryable via the reference-data API
		(GET /api/v1/refdata/securities) and this knowledge search.""";

	private final SecurityMasterRepository repository;
	private final boolean enabled;
	private final int windowSize;

	public ReferenceDataChunker(SecurityMasterRepository repository,
			@Value("${rag.reference-data.enabled:true}") boolean enabled,
			@Value("${rag.reference-data.window-size:20}") int windowSize) {
		this.repository = repository;
		this.enabled = enabled;
		this.windowSize = Math.max(1, windowSize);
	}

	/** Reference-data chunks for the current rows; empty when disabled or the master is empty. */
	public List<DocChunk> chunks() {
		if (!enabled) {
			return List.of();
		}
		List<SecurityMaster> securities = repository.findAll(Sort.by(Sort.Direction.ASC, "instrumentId"));
		if (securities.isEmpty()) {
			return List.of();
		}
		List<DocChunk> chunks = new ArrayList<>();
		chunks.add(DocChunk.of(OVERVIEW_SOURCE, "Security-master reference data", OVERVIEW_CONTENT));
		for (int start = 0; start < securities.size(); start += windowSize) {
			int end = Math.min(start + windowSize, securities.size());
			List<SecurityMaster> window = securities.subList(start, end);
			String source = "%s-%s-%s".formatted(WINDOW_SOURCE_PREFIX,
				window.get(0).getInstrumentId(), window.get(window.size() - 1).getInstrumentId());
			chunks.add(DocChunk.of(source, "Securities", render(window)));
		}
		return chunks;
	}

	/** Render a window of securities as one compact, retrievable text block (one row per line). */
	private String render(List<SecurityMaster> window) {
		StringBuilder sb = new StringBuilder("Security-master reference records:\n");
		for (SecurityMaster s : window) {
			sb.append(s.getInstrumentId())
				.append(" | ").append(s.getName())
				.append(" | assetClass=").append(s.getAssetClass())
				.append(" | currency=").append(s.getCurrency())
				.append(" | ISIN=").append(s.getIsin())
				.append(" | CUSIP=").append(s.getCusip())
				.append(" | SEDOL=").append(s.getSedol())
				.append(" | price=").append(s.getPrice())
				.append(" | asOf=").append(s.getAsOfDate())
				.append('\n');
		}
		return sb.toString();
	}
}
