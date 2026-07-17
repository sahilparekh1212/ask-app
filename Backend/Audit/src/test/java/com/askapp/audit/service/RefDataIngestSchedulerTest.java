package com.askapp.audit.service;

import com.askapp.audit.dto.IngestResponse;
import com.askapp.audit.rag.RagIndexer;
import com.askapp.audit.repository.SecurityMasterRepository;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RefDataIngestSchedulerTest {

	private final SecurityMasterRepository repository = mock(SecurityMasterRepository.class);
	private final RefDataIngestService ingestService = mock(RefDataIngestService.class);
	private final RagIndexer ragIndexer = mock(RagIndexer.class);

	private RefDataIngestScheduler scheduler(int count) {
		return new RefDataIngestScheduler(repository, ingestService, ragIndexer, count);
	}

	@Test
	void appendsFromTheCurrentRowCountThenReindexes() {
		when(repository.count()).thenReturn(1000L);
		when(ingestService.ingest(1000, 50)).thenReturn(new IngestResponse(50, 50, "spring-batch"));

		scheduler(50).ingestDailyBatch();

		// Start index is the current count, so the generated range is entirely new.
		verify(ingestService).ingest(1000, 50);
		verify(ragIndexer).reindex();
	}

	@Test
	void nonPositiveCountIsANoOp() {
		scheduler(0).ingestDailyBatch();

		verifyNoInteractions(ingestService, ragIndexer);
		verify(repository, never()).count();
	}
}
