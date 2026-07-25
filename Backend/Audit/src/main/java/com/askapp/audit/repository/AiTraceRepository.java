package com.askapp.audit.repository;

import com.askapp.audit.model.AiTrace;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence for AI interaction traces (ADR-0014). Write-mostly: {@code AiTraceService} inserts a
 * row per chat / MCP search; reads are ad-hoc SQL / the {@code v_ai_trace_daily} view for analysis.
 */
public interface AiTraceRepository extends JpaRepository<AiTrace, Long> {
}
