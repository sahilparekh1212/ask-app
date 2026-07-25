package com.askapp.audit.trace;

import com.askapp.audit.model.AiTrace;
import com.askapp.audit.rag.RagService;
import com.askapp.audit.rag.ScoredChunk;
import com.askapp.audit.repository.AiTraceRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Persists AI interaction traces (ADR-0014), best-effort and off the request path. Mapping applies
 * the content-capture flag (drops raw query/reply text when off, keeps the retrieval scores/ranks,
 * which are public corpus metadata) and caps each text field to its column width. The write is
 * {@code @Async} fire-and-forget with errors swallowed — the ADR-0006 "tracing must never slow or
 * fail the thing it observes" posture.
 */
@Service
public class AiTraceService {

	// Caps matching the ai_trace column widths (VARCHAR(4000) / VARCHAR(8000)).
	static final int MAX_QUERY = 4000;
	static final int MAX_TEXT = 8000;

	private static final Logger log = LoggerFactory.getLogger(AiTraceService.class);

	private final AiTraceRepository repository;
	private final AiTraceProperties properties;
	private final ObjectMapper objectMapper;

	public AiTraceService(AiTraceRepository repository, AiTraceProperties properties, ObjectMapper objectMapper) {
		this.repository = repository;
		this.properties = properties;
		this.objectMapper = objectMapper;
	}

	@Async
	public void record(AiTraceRecord rec) {
		if (!properties.isEnabled()) {
			return;
		}
		try {
			repository.save(toEntity(rec));
		} catch (RuntimeException e) {
			log.warn("AI trace persist failed (ignored): {}", e.getClass().getSimpleName());
		}
	}

	AiTrace toEntity(AiTraceRecord rec) {
		AiTrace trace = new AiTrace();
		trace.setFeature(rec.feature());
		trace.setModel(rec.model());
		trace.setBlocked(rec.blocked());
		trace.setBlockedCategory(rec.blockedCategory());
		trace.setAdmin(rec.admin());
		trace.setAuditGrounded(rec.auditGrounded());
		trace.setRetrievalMs(rec.retrievalMs());
		trace.setLlmMs(rec.llmMs());
		trace.setInputTokens(rec.inputTokens());
		trace.setOutputTokens(rec.outputTokens());
		if (rec.reply() != null) {
			trace.setReplyLength(rec.reply().length());
		}
		// Content flag gates only the raw text; scores/ranks/sources below are always captured.
		if (properties.isCaptureContent()) {
			trace.setQueryText(truncate(rec.query(), MAX_QUERY));
			trace.setReplyText(truncate(rec.reply(), MAX_TEXT));
		}
		RagService.Retrieval retrieval = rec.retrieval();
		if (retrieval != null) {
			List<ScoredChunk> results = retrieval.results();
			List<ScoredChunk> candidates = retrieval.candidates();
			trace.setRetrievedCount(results.size());
			trace.setCandidateCount(candidates.size());
			// The reranker widens the candidate pool beyond the returned top-k; equal sizes = identity.
			trace.setReranked(candidates.size() > results.size());
			if (!results.isEmpty()) {
				trace.setTopScore(results.get(0).score());
			}
			trace.setRetrievedJson(truncate(toJson(results, true), MAX_TEXT));
			trace.setCandidatesJson(truncate(toJson(candidates, false), MAX_QUERY));
		}
		return trace;
	}

	private String toJson(List<ScoredChunk> chunks, boolean withHeading) {
		List<Map<String, Object>> rows = new ArrayList<>(chunks.size());
		for (int i = 0; i < chunks.size(); i++) {
			ScoredChunk chunk = chunks.get(i);
			Map<String, Object> row = new LinkedHashMap<>();
			row.put("rank", i + 1);
			row.put("source", chunk.source());
			if (withHeading) {
				row.put("heading", chunk.heading());
			}
			row.put("score", Math.round(chunk.score() * 10000.0) / 10000.0);
			rows.add(row);
		}
		try {
			return objectMapper.writeValueAsString(rows);
		} catch (JsonProcessingException e) {
			return null;
		}
	}

	private static String truncate(String value, int max) {
		if (value == null) {
			return null;
		}
		return value.length() <= max ? value : value.substring(0, max);
	}

}
