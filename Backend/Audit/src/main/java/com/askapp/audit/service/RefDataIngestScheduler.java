package com.askapp.audit.service;

import com.askapp.audit.dto.IngestResponse;
import com.askapp.audit.rag.RagIndexer;
import com.askapp.audit.repository.SecurityMasterRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Appends a small daily batch of new security-master reference rows via the Spring Batch ingestion
 * job, then triggers a RAG re-index so the freshly-added rows become retrievable without a restart.
 *
 * <p>The start index is the current row count, so each run generates an entirely new instrument
 * range (index {@code count..count+size-1}) rather than regenerating index 0, which the batch's
 * dedupe step would otherwise discard. Reference data is append-only (the retention purge targets
 * audit logs, not the master), so the count stays a valid, gap-free next-index.
 *
 * <p>Disabled with {@code refdata.ingest.schedule.enabled=false} (off in tests); cadence and size
 * are {@code refdata.ingest.schedule.cron} (default 02:00 daily) and
 * {@code refdata.ingest.schedule.count} (default 50).
 */
@Component
@ConditionalOnProperty(name = "refdata.ingest.schedule.enabled", havingValue = "true", matchIfMissing = true)
public class RefDataIngestScheduler {

	private static final Logger log = LoggerFactory.getLogger(RefDataIngestScheduler.class);

	private final SecurityMasterRepository repository;
	private final RefDataIngestService ingestService;
	private final RagIndexer ragIndexer;
	private final int count;

	public RefDataIngestScheduler(SecurityMasterRepository repository, RefDataIngestService ingestService,
			RagIndexer ragIndexer, @Value("${refdata.ingest.schedule.count:50}") int count) {
		this.repository = repository;
		this.ingestService = ingestService;
		this.ragIndexer = ragIndexer;
		this.count = count;
	}

	@Scheduled(cron = "${refdata.ingest.schedule.cron:0 0 2 * * *}")
	public void ingestDailyBatch() {
		if (count < 1) {
			return;
		}
		int startIndex = (int) repository.count();
		IngestResponse response = ingestService.ingest(startIndex, count);
		log.info("Daily reference-data batch: appended {} new rows from index {}", response.ingested(), startIndex);
		// Re-index so the new rows are retrievable via RAG/MCP. No-op when RAG is not configured;
		// incremental hashing means only the genuinely-new reference chunks get embedded.
		ragIndexer.reindex();
	}
}
