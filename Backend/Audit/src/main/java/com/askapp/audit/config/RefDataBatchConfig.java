package com.askapp.audit.config;

import com.askapp.audit.model.SecurityMaster;
import com.askapp.audit.repository.SecurityMasterRepository;
import com.askapp.audit.service.RefDataGenerator;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Iterator;

/**
 * Spring Batch job for bulk reference-data ingestion: a single chunk-oriented step reads generated
 * security-master records, filters out ones already present (idempotency), and writes the rest.
 *
 * <p>The record count comes from the {@code count} job parameter (0 if absent), so a startup
 * auto-run — before {@code spring.batch.job.enabled=false} takes effect — is a harmless no-op
 * rather than an error. The JobRepository's metadata schema is created by Liquibase (changeset
 * {@code 004-spring-batch-metadata}), so nothing here initializes it.
 */
@Configuration
public class RefDataBatchConfig {

	public static final String INGEST_JOB = "securityMasterIngestJob";

	private static final int CHUNK_SIZE = 500;

	@Bean
	public Job securityMasterIngestJob(JobRepository jobRepository, Step securityMasterIngestStep) {
		return new JobBuilder(INGEST_JOB, jobRepository)
			.start(securityMasterIngestStep)
			.build();
	}

	@Bean
	public Step securityMasterIngestStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
			ItemReader<SecurityMaster> securityItemReader,
			ItemProcessor<SecurityMaster, SecurityMaster> securityDedupeProcessor,
			SecurityMasterRepository repository) {
		return new StepBuilder("securityMasterIngestStep", jobRepository)
			.<SecurityMaster, SecurityMaster>chunk(CHUNK_SIZE, transactionManager)
			.reader(securityItemReader)
			.processor(securityDedupeProcessor)
			.writer(chunk -> repository.saveAll(chunk.getItems()))
			.build();
	}

	/**
	 * Step-scoped so {@code count} and {@code startIndex} bind from the running job's parameters
	 * (0 if absent). {@code startIndex} lets the daily incremental job append a fresh range instead
	 * of regenerating index 0; the initial bulk load and the manual {@code /ingest} endpoint leave
	 * it at 0.
	 */
	@Bean
	@StepScope
	public ItemReader<SecurityMaster> securityItemReader(RefDataGenerator generator,
			@Value("#{jobParameters['startIndex'] ?: 0}") long startIndex,
			@Value("#{jobParameters['count'] ?: 0}") long count) {
		Iterator<SecurityMaster> iterator = generator.generate((int) startIndex, (int) count).iterator();
		return () -> iterator.hasNext() ? iterator.next() : null;
	}

	/** Skip records whose instrument id already exists — makes a re-run of the same range a no-op. */
	@Bean
	public ItemProcessor<SecurityMaster, SecurityMaster> securityDedupeProcessor(SecurityMasterRepository repository) {
		return item -> repository.existsByInstrumentId(item.getInstrumentId()) ? null : item;
	}
}
