package com.askapp.audit.service;

import com.askapp.audit.dto.IngestResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.launch.JobLauncher;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RefDataIngestServiceTest {

	private JobLauncher jobLauncher;
	private Job job;
	private RefDataIngestService service;

	@BeforeEach
	void setUp() {
		jobLauncher = mock(JobLauncher.class);
		job = mock(Job.class);
		service = new RefDataIngestService(jobLauncher, job);
	}

	@Test
	void ingest_sumsStepWriteCountsIntoTheResponse() throws Exception {
		StepExecution step = mock(StepExecution.class);
		when(step.getWriteCount()).thenReturn(7L);
		JobExecution execution = mock(JobExecution.class);
		when(execution.getStepExecutions()).thenReturn(List.of(step));
		when(jobLauncher.run(eq(job), any(JobParameters.class))).thenReturn(execution);

		IngestResponse response = service.ingest(7);

		assertThat(response.requested()).isEqualTo(7);
		assertThat(response.ingested()).isEqualTo(7L);
		assertThat(response.engine()).isEqualTo("spring-batch");
	}

	@Test
	void ingest_wrapsLaunchFailures() throws Exception {
		when(jobLauncher.run(any(), any())).thenThrow(new IllegalStateException("boom"));

		assertThatThrownBy(() -> service.ingest(5))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("ingestion job failed to launch");
	}
}
