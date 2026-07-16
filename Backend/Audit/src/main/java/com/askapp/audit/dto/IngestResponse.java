package com.askapp.audit.dto;

/**
 * Result of a reference-data ingestion run.
 *
 * @param requested how many records the caller asked to ingest
 * @param ingested  how many were actually written (duplicates by instrument id are skipped, so
 *                  a re-run of the same range writes 0 — ingestion is idempotent)
 * @param engine    which ingestion engine ran ({@code generator} now; {@code spring-batch} once
 *                  the batch job replaces the inline loader)
 */
public record IngestResponse(int requested, long ingested, String engine) {
}
