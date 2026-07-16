package com.askapp.audit.service;

import com.askapp.audit.dto.IngestResponse;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Launches the security-master ingestion Spring Batch job. A unique {@code run.id} parameter makes
 * each call a fresh job instance, and the response reports how many records the step actually wrote
 * — duplicates are filtered by the step's processor, so a re-run of the same range writes 0.
 */
@Service
public class RefDataIngestService {

	// Strictly-increasing run id (seeded from the wall clock so a restart never collides with a
	// JobInstance persisted before it), guaranteeing every launch is a distinct job instance.
	private static final AtomicLong RUN_ID = new AtomicLong(System.currentTimeMillis());

	private final JobLauncher jobLauncher;
	private final Job securityMasterIngestJob;

	public RefDataIngestService(JobLauncher jobLauncher, Job securityMasterIngestJob) {
		this.jobLauncher = jobLauncher;
		this.securityMasterIngestJob = securityMasterIngestJob;
	}

	public IngestResponse ingest(int count) {
		JobParameters parameters = new JobParametersBuilder()
			.addLong("count", (long) count)
			.addLong("run.id", RUN_ID.incrementAndGet())
			.toJobParameters();
		try {
			JobExecution execution = jobLauncher.run(securityMasterIngestJob, parameters);
			long written = execution.getStepExecutions().stream()
				.mapToLong(StepExecution::getWriteCount)
				.sum();
			return new IngestResponse(count, written, "spring-batch");
		}
		catch (Exception e) {
			throw new IllegalStateException("Reference-data ingestion job failed to launch", e);
		}
	}
}
