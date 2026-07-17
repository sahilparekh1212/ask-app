package com.askapp.audit.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on Spring's {@code @Scheduled} support for the service's periodic jobs: the daily
 * reference-data incremental ingest ({@code RefDataIngestScheduler}) and the audit-log retention
 * purge ({@code AuditLogPurgeScheduler}). Each of those beans is independently toggleable via its
 * own {@code *.enabled} property, so scheduling can be enabled here while an individual job is
 * turned off (e.g. in tests).
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
