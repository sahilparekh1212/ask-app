package com.askapp.audit.service;

import com.askapp.audit.dto.IngestResponse;
import com.askapp.audit.repository.SecurityMasterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end ingestion through the real Spring Batch job: exercises {@code RefDataBatchConfig}
 * (reader, dedupe processor, writer, job/step) and {@link RefDataIngestService} against H2 with the
 * Liquibase-created batch metadata schema.
 */
@SpringBootTest
class RefDataBatchIngestIntegrationTest {

	@Autowired
	private RefDataIngestService ingestService;

	@Autowired
	private SecurityMasterRepository repository;

	@BeforeEach
	void clean() {
		repository.deleteAll();
	}

	@Test
	void ingest_runsTheBatchJobAndPersistsRecords() {
		IngestResponse response = ingestService.ingest(5);

		assertThat(response.requested()).isEqualTo(5);
		assertThat(response.ingested()).isEqualTo(5);
		assertThat(response.engine()).isEqualTo("spring-batch");
		assertThat(repository.count()).isEqualTo(5);
		assertThat(repository.findByInstrumentId("SEC-000000")).isPresent();
	}

	@Test
	void ingest_isIdempotent_reRunWritesNothingNew() {
		ingestService.ingest(3);
		IngestResponse second = ingestService.ingest(3);

		assertThat(second.ingested()).isZero();
		assertThat(repository.count()).isEqualTo(3);
	}
}
