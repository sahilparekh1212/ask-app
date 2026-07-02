package com.aisandbox.audit.config;

import com.aisandbox.audit.model.AuditLog;
import com.aisandbox.audit.repository.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DemoDataSeederTest {

	private final AuditLogRepository repository = mock(AuditLogRepository.class);

	@Test
	void seedsRowsWhenTheTableIsEmpty() {
		when(repository.count()).thenReturn(0L);
		DemoDataSeeder seeder = new DemoDataSeeder(repository, true);

		seeder.run();

		verify(repository).saveAll(anyList());
	}

	@Test
	void skipsSeedingWhenTheTableAlreadyHasRows() {
		when(repository.count()).thenReturn(3L);
		DemoDataSeeder seeder = new DemoDataSeeder(repository, true);

		seeder.run();

		verify(repository, never()).saveAll(anyList());
	}

	@Test
	void skipsSeedingWhenDisabled() {
		DemoDataSeeder seeder = new DemoDataSeeder(repository, false);

		seeder.run();

		verify(repository, never()).count();
		verify(repository, never()).saveAll(anyList());
	}

	@Test
	@SuppressWarnings("unchecked")
	void seedsAtLeastACoupleOfDistinctEntityTypesAndActions() {
		when(repository.count()).thenReturn(0L);
		DemoDataSeeder seeder = new DemoDataSeeder(repository, true);

		seeder.run();

		ArgumentCaptor<List<AuditLog>> captor = ArgumentCaptor.forClass(List.class);
		verify(repository).saveAll(captor.capture());
		List<AuditLog> seeded = captor.getValue();

		assertThat(seeded).hasSizeGreaterThanOrEqualTo(10);
		assertThat(seeded.stream().map(AuditLog::getEntityType).distinct().count()).isGreaterThanOrEqualTo(3);
		assertThat(seeded.stream().map(AuditLog::getAction).distinct().count()).isGreaterThanOrEqualTo(3);
	}

}
