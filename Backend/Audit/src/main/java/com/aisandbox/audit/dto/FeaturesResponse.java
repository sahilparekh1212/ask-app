package com.aisandbox.audit.dto;

/**
 * Runtime capability flags the SPA reads to adapt its UI to the deployment it's talking to.
 * {@code demoData} is whether the LOCAL/DEV-only demo-data generator is available.
 */
public record FeaturesResponse(boolean demoData) {
}
