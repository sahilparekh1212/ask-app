package com.askapp.audit.rag;

import com.askapp.audit.model.SecurityMaster;
import com.askapp.audit.repository.SecurityMasterRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ReferenceDataChunkerTest {

	private final SecurityMasterRepository repository = mock(SecurityMasterRepository.class);

	private ReferenceDataChunker chunker(boolean enabled, int windowSize) {
		return new ReferenceDataChunker(repository, enabled, windowSize);
	}

	private static SecurityMaster security(int i) {
		return new SecurityMaster("SEC-%06d".formatted(i), "US%010d".formatted(i), "CUSIP%04d".formatted(i),
			"SED%04d".formatted(i), "Synthetic EQUITY", "EQUITY", "USD", new BigDecimal("10.00"),
			LocalDate.of(2026, 7, 16));
	}

	@Test
	void producesAnOverviewChunkPlusOneWindowPerGroupOfRows() {
		when(repository.findAll(any(Sort.class)))
			.thenReturn(IntStream.range(0, 5).mapToObj(ReferenceDataChunkerTest::security).toList());

		List<DocChunk> chunks = chunker(true, 2).chunks();

		// 1 overview + ceil(5/2)=3 windows.
		assertThat(chunks).hasSize(4);
		assertThat(chunks.get(0).source()).isEqualTo("reference-data/overview");
		assertThat(chunks).extracting(DocChunk::source)
			.contains("reference-data/securities-SEC-000000-SEC-000001",
				"reference-data/securities-SEC-000004-SEC-000004");
		// The rows' identifiers make it into the retrievable content.
		assertThat(chunks.get(1).content()).contains("SEC-000000").contains("assetClass=EQUITY");
	}

	@Test
	void chunkIdsAreStableAcrossReindexingSoUnchangedWindowsDoNotReembed() {
		when(repository.findAll(any(Sort.class)))
			.thenReturn(IntStream.range(0, 4).mapToObj(ReferenceDataChunkerTest::security).toList());

		List<String> first = chunker(true, 2).chunks().stream().map(DocChunk::id).toList();
		List<String> second = chunker(true, 2).chunks().stream().map(DocChunk::id).toList();

		assertThat(second).isEqualTo(first);
	}

	@Test
	void emptyWhenDisabled_withoutTouchingTheDatabase() {
		assertThat(chunker(false, 20).chunks()).isEmpty();
		verifyNoInteractions(repository);
	}

	@Test
	void emptyWhenNoRows() {
		when(repository.findAll(any(Sort.class))).thenReturn(List.of());

		assertThat(chunker(true, 20).chunks()).isEmpty();
	}
}
