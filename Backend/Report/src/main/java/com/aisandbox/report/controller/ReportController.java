package com.aisandbox.report.controller;

import com.aisandbox.report.aggregation.AggregatedUuidsResponse;
import com.aisandbox.report.aggregation.UuidAggregationService;
import com.aisandbox.report.exception.ResourceNotFoundException;
import com.aisandbox.report.model.Report;
import com.aisandbox.report.ratelimit.TransactionalRequestExecutor;
import com.aisandbox.report.repository.ReportRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/reports")
@Tag(name = "Reports", description = "Batch report management")
public class ReportController {

	private static final Logger log = LoggerFactory.getLogger(ReportController.class);

	private final JobLauncher jobLauncher;
	private final Job reportJob;
	private final ReportRepository reportRepository;
	// Wraps mutations so a superseding request rolls the work back transactionally.
	private final TransactionalRequestExecutor txExecutor;
	private final UuidAggregationService uuidAggregationService;

	public ReportController(JobLauncher jobLauncher, Job reportJob, ReportRepository reportRepository,
			TransactionalRequestExecutor txExecutor, UuidAggregationService uuidAggregationService) {
		this.jobLauncher = jobLauncher;
		this.reportJob = reportJob;
		this.reportRepository = reportRepository;
		this.txExecutor = txExecutor;
		this.uuidAggregationService = uuidAggregationService;
	}

	@GetMapping
	@Operation(summary = "List all generated reports")
	public List<Report> findAll() {
		log.info("Fetching all reports");
		return reportRepository.findAll();
	}

	@GetMapping("/{id}")
	@Operation(summary = "Get report by ID")
	public Report findById(@PathVariable Long id) {
		log.info("Fetching report id={}", id);
		return reportRepository.findById(id)
			.orElseThrow(() -> new ResourceNotFoundException("Report not found: " + id));
	}

	@PostMapping("/run")
	@Operation(summary = "Trigger the report batch job")
	public ResponseEntity<String> runJob() throws Exception {
		JobParameters params = new JobParametersBuilder()
			.addLong("timestamp", System.currentTimeMillis())
			.toJobParameters();
		jobLauncher.run(reportJob, params);
		log.info("Report batch job triggered");
		return ResponseEntity.ok("Report job started");
	}

	@PostMapping("/uuids")
	@Operation(summary = "Aggregate request UUIDs of all transaction/audit/notification records "
		+ "(including soft-deleted) across all accounts")
	public AggregatedUuidsResponse aggregateUuids(
			@RequestHeader(value = "Authorization", required = false) String authorization) {
		log.info("Aggregating cross-service request UUIDs (including soft-deleted)");
		return uuidAggregationService.aggregate(authorization, MDC.get("requestId"));
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Delete a report")
	public void delete(@PathVariable Long id) {
		txExecutor.run(() -> {
			if (!reportRepository.existsById(id)) {
				throw new ResourceNotFoundException("Report not found: " + id);
			}
			reportRepository.deleteById(id);
			return null;
		});
		log.info("Deleted report id={}", id);
	}

	@GetMapping("/health")
	@Operation(summary = "Health check")
	public ResponseEntity<String> health() {
		return ResponseEntity.ok("report-service OK");
	}

}
