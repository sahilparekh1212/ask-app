package com.askapp.audit.rag;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * {@link VectorStore} backed by Postgres + the pgvector extension — the "vector database"
 * half of this feature, active when {@code rag.vector-store=pgvector} (the Docker/DEV+
 * stack). Schema is owned by Liquibase like every other table (changeset
 * {@code 002-rag-chunk}, gated {@code dbms: postgresql}); access is plain {@code JdbcTemplate}
 * rather than JPA because the {@code vector} column type and the {@code <=>} cosine-distance
 * operator have no JPA mapping worth pretending exists.
 */
@Component
@ConditionalOnProperty(name = "rag.vector-store", havingValue = "pgvector")
public class PgVectorVectorStore implements VectorStore {

	private final JdbcTemplate jdbc;

	public PgVectorVectorStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public Set<String> existingIds() {
		return new HashSet<>(jdbc.queryForList("SELECT id FROM rag_chunk", String.class));
	}

	@Override
	public void upsert(List<DocChunk> chunks, List<float[]> vectors) {
		if (chunks.size() != vectors.size()) {
			throw new IllegalArgumentException("chunks and vectors must be the same size");
		}
		for (int i = 0; i < chunks.size(); i++) {
			DocChunk chunk = chunks.get(i);
			// ON CONFLICT DO NOTHING keeps re-runs idempotent: the id is a content hash, so a
			// duplicate id is byte-identical content — nothing to update.
			jdbc.update("""
				INSERT INTO rag_chunk (id, source, heading, content, embedding, indexed_at)
				VALUES (?, ?, ?, ?, ?::vector, ?)
				ON CONFLICT (id) DO NOTHING
				""",
				chunk.id(), chunk.source(), chunk.heading(), chunk.content(),
				toVectorLiteral(vectors.get(i)), Timestamp.from(Instant.now()));
		}
	}

	@Override
	public void retainOnly(Set<String> liveIds) {
		Set<String> stale = existingIds();
		stale.removeAll(liveIds);
		for (String id : stale) {
			jdbc.update("DELETE FROM rag_chunk WHERE id = ?", id);
		}
	}

	@Override
	public List<ScoredChunk> search(float[] query, int topK) {
		String literal = toVectorLiteral(query);
		// <=> is pgvector's cosine distance; similarity = 1 - distance. Ordering by the
		// operator directly is what lets a future ivfflat/hnsw index serve the query.
		return jdbc.query("""
			SELECT source, heading, content, 1 - (embedding <=> ?::vector) AS score
			FROM rag_chunk
			ORDER BY embedding <=> ?::vector
			LIMIT ?
			""",
			(rs, rowNum) -> new ScoredChunk(rs.getString("source"), rs.getString("heading"),
				rs.getString("content"), rs.getDouble("score")),
			literal, literal, topK);
	}

	@Override
	public List<SourceSummary> sources() {
		return jdbc.query(
			"SELECT source, count(*) AS chunk_count FROM rag_chunk GROUP BY source ORDER BY source",
			(rs, rowNum) -> new SourceSummary(rs.getString("source"), rs.getLong("chunk_count")));
	}

	@Override
	public long count() {
		Long count = jdbc.queryForObject("SELECT count(*) FROM rag_chunk", Long.class);
		return count == null ? 0 : count;
	}

	/** pgvector's input syntax: {@code [0.1,0.2,...]}. */
	static String toVectorLiteral(float[] vector) {
		StringBuilder sb = new StringBuilder("[");
		for (int i = 0; i < vector.length; i++) {
			if (i > 0) {
				sb.append(',');
			}
			sb.append(vector[i]);
		}
		return sb.append(']').toString();
	}

}
