package com.askapp.audit.config;

import com.askapp.audit.dto.IngestResponse;
import com.askapp.audit.repository.SecurityMasterRepository;
import com.askapp.audit.service.RefDataIngestService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RefDataSeederTest {

	private final SecurityMasterRepository repository = mock(SecurityMasterRepository.class);
	private final RefDataIngestService ingestService = mock(RefDataIngestService.class);

	private RefDataSeeder seeder(boolean enabled, int count) {
		return new RefDataSeeder(repository, ingestService, enabled, count);
	}

	@Test
	void seedsTheConfiguredCountWhenTheTableIsEmpty() {
		when(repository.count()).thenReturn(0L);
		when(ingestService.ingest(1000)).thenReturn(new IngestResponse(1000, 1000, "spring-batch"));

		seeder(true, 1000).run(null);

		verify(ingestService).ingest(1000);
	}

	@Test
	void skipsWhenRowsAlreadyExist() {
		when(repository.count()).thenReturn(1000L);

		seeder(true, 1000).run(null);

		verifyNoInteractions(ingestService);
	}

	@Test
	void skipsWhenDisabled_withoutQueryingTheTable() {
		seeder(false, 1000).run(null);

		verifyNoInteractions(repository, ingestService);
	}

	@Test
	void skipsWhenCountIsNonPositive() {
		seeder(true, 0).run(null);

		verify(ingestService, never()).ingest(org.mockito.ArgumentMatchers.anyInt());
	}
}
