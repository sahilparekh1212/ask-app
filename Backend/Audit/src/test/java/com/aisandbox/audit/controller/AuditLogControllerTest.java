package com.aisandbox.audit.controller;

import com.aisandbox.audit.exception.ResourceNotFoundException;
import com.aisandbox.audit.model.AuditLog;
import com.aisandbox.audit.ratelimit.TransactionalRequestExecutor;
import com.aisandbox.audit.repository.AuditLogRepository;
import com.aisandbox.audit.service.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditLogControllerTest {

	private AuditLogRepository repository;
	private AuditLogController controller;

	@BeforeEach
	void setUp() {
		repository = mock(AuditLogRepository.class);
		TransactionalRequestExecutor txExecutor = mock(TransactionalRequestExecutor.class);
		when(txExecutor.run(any())).thenAnswer(invocation -> {
			Supplier<?> work = invocation.getArgument(0);
			return work.get();
		});
		AuditLogService auditLogService = mock(AuditLogService.class);
		controller = new AuditLogController(repository, auditLogService, txExecutor);
	}

	@Test
	void findAll_excludesSoftDeletedByDefault() {
		AuditLog active = new AuditLog("User", "CREATE", "details");
		when(repository.findByDeletedFalse()).thenReturn(List.of(active));

		List<AuditLog> result = controller.findAll(false);

		assertThat(result).containsExactly(active);
		verify(repository, org.mockito.Mockito.never()).findAll();
	}

	@Test
	void findAll_includesSoftDeletedWhenRequested() {
		AuditLog deleted = new AuditLog("User", "DELETE", "details");
		deleted.setDeleted(true);
		when(repository.findAll()).thenReturn(List.of(deleted));

		List<AuditLog> result = controller.findAll(true);

		assertThat(result).containsExactly(deleted);
	}

	@Test
	void findById_returnsTheMatchingLog() {
		AuditLog log = new AuditLog("User", "CREATE", "details");
		when(repository.findById(1L)).thenReturn(Optional.of(log));

		AuditLog result = controller.findById(1L);

		assertThat(result).isSameAs(log);
	}

	@Test
	void findById_throwsResourceNotFoundWhenMissing() {
		when(repository.findById(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> controller.findById(99L))
			.isInstanceOf(ResourceNotFoundException.class)
			.hasMessageContaining("99");
	}

	@Test
	void create_savesAndReturnsTheLog() {
		AuditLog input = new AuditLog("User", "CREATE", "details");
		when(repository.save(input)).thenReturn(input);

		AuditLog result = controller.create(input);

		assertThat(result).isSameAs(input);
		verify(repository).save(input);
	}

	@Test
	void delete_softDeletesAnExistingLog() {
		AuditLog log = new AuditLog("User", "CREATE", "details");
		when(repository.findById(1L)).thenReturn(Optional.of(log));
		when(repository.save(log)).thenReturn(log);

		controller.delete(1L);

		assertThat(log.isDeleted()).isTrue();
		verify(repository).save(log);
	}

	@Test
	void delete_throwsResourceNotFoundWhenMissing() {
		when(repository.findById(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> controller.delete(99L))
			.isInstanceOf(ResourceNotFoundException.class);
	}

	@Test
	void health_returnsOk() {
		var response = controller.health();

		assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
		assertThat(response.getBody()).isEqualTo("audit-service OK");
	}
}
