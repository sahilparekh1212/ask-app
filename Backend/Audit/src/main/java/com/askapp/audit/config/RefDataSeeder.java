package com.askapp.audit.config;

import com.askapp.audit.dto.IngestResponse;
import com.askapp.audit.repository.SecurityMasterRepository;
import com.askapp.audit.service.RefDataIngestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Seeds an initial batch of security-master reference rows on startup so the reference-data API
 * and the dashboard's reference panel show real data on first run, and so the RAG indexer has
 * something to index. Reuses the Spring Batch ingestion job (via {@link RefDataIngestService}) —
 * the same path the manual {@code /ingest} endpoint and the daily incremental scheduler use — so
 * there is one code path that writes reference data.
 *
 * <p>Idempotent: skips entirely if the table already has rows, so it seeds exactly once against a
 * persistent Postgres and re-seeds each restart only on the in-memory H2 (LOCAL), which resets
 * anyway. Runs at {@link Ordered#HIGHEST_PRECEDENCE} so the rows exist before {@code RagIndexer}
 * (a later {@code ApplicationRunner}) reads the corpus on its background thread. Disable with
 * {@code refdata.seed.enabled=false}; size it with {@code refdata.seed.count} (default 1000).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RefDataSeeder implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(RefDataSeeder.class);

	private final SecurityMasterRepository repository;
	private final RefDataIngestService ingestService;
	private final boolean enabled;
	private final int count;

	public RefDataSeeder(SecurityMasterRepository repository, RefDataIngestService ingestService,
			@Value("${refdata.seed.enabled:true}") boolean enabled,
			@Value("${refdata.seed.count:1000}") int count) {
		this.repository = repository;
		this.ingestService = ingestService;
		this.enabled = enabled;
		this.count = count;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (!enabled || count < 1) {
			return;
		}
		if (repository.count() > 0) {
			log.info("Skipping reference-data seed — security_master already has rows");
			return;
		}
		IngestResponse response = ingestService.ingest(count);
		log.info("Seeded {} of {} requested security-master reference rows", response.ingested(), count);
	}

}
