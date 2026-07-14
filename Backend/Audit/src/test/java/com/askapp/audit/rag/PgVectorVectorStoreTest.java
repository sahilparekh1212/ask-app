package com.askapp.audit.rag;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit-level: verifies the SQL contract and the pgvector literal encoding against a mocked
 * JdbcTemplate. The real pgvector round-trip is exercised by the Docker stack (H2 in CI has
 * no pgvector extension to test against — that constraint is exactly why the store is
 * swappable; see ADR-0010).
 */
class PgVectorVectorStoreTest {

	private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
	private final PgVectorVectorStore store = new PgVectorVectorStore(jdbc);

	@Test
	void vectorLiteralUsesPgvectorInputSyntax() {
		assertThat(PgVectorVectorStore.toVectorLiteral(new float[] {1.5f, -2.0f, 0f}))
			.isEqualTo("[1.5,-2.0,0.0]");
	}

	@Test
	void upsertInsertsWithConflictIgnoreAndVectorCast() {
		DocChunk chunk = DocChunk.of("a.md", "H", "content");

		store.upsert(List.of(chunk), List.of(new float[] {0.5f, 0.25f}));

		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
		verify(jdbc).update(sql.capture(), args.capture());
		assertThat(sql.getValue()).contains("ON CONFLICT (id) DO NOTHING").contains("?::vector");
		assertThat(args.getValue()[0]).isEqualTo(chunk.id());
		assertThat(args.getValue()[4]).isEqualTo("[0.5,0.25]");
	}

	@Test
	void upsertRejectsMismatchedSizes() {
		assertThatThrownBy(() -> store.upsert(List.of(DocChunk.of("a.md", "H", "x")), List.of()))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void retainOnlyDeletesOnlyStaleIds() {
		when(jdbc.queryForList("SELECT id FROM rag_chunk", String.class))
			.thenReturn(List.of("live", "stale"));

		store.retainOnly(Set.of("live"));

		verify(jdbc).update("DELETE FROM rag_chunk WHERE id = ?", "stale");
		verify(jdbc, never()).update("DELETE FROM rag_chunk WHERE id = ?", "live");
	}

	@Test
	void searchOrdersByCosineDistanceOperator() {
		when(jdbc.query(anyString(), any(RowMapper.class), any(), any(), any()))
			.thenReturn(List.of(new ScoredChunk("a.md", "H", "text", 0.9)));

		List<ScoredChunk> results = store.search(new float[] {1f, 0f}, 3);

		assertThat(results).hasSize(1);
		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		verify(jdbc).query(sql.capture(), any(RowMapper.class), eq("[1.0,0.0]"), eq("[1.0,0.0]"), eq(3));
		assertThat(sql.getValue()).contains("<=>").contains("ORDER BY embedding");
	}

	@Test
	void sourcesGroupsBySourceInSql() {
		store.sources();

		verify(jdbc).query(contains("GROUP BY source"), any(RowMapper.class));
	}

	@Test
	void countHandlesNullAsZero() {
		when(jdbc.queryForObject("SELECT count(*) FROM rag_chunk", Long.class)).thenReturn(null);
		assertThat(store.count()).isZero();

		when(jdbc.queryForObject("SELECT count(*) FROM rag_chunk", Long.class)).thenReturn(7L);
		assertThat(store.count()).isEqualTo(7);
	}

	@Test
	void existingIdsReadsIdColumn() {
		when(jdbc.queryForList("SELECT id FROM rag_chunk", String.class)).thenReturn(List.of("a", "b"));

		assertThat(store.existingIds()).containsExactlyInAnyOrder("a", "b");
	}

}
