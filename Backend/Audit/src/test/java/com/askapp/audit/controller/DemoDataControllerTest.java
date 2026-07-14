package com.askapp.audit.controller;

import com.askapp.audit.dto.DemoDataRequest;
import com.askapp.audit.dto.DemoDataResponse;
import com.askapp.audit.exception.GlobalExceptionHandler;
import com.askapp.audit.model.AuditLog;
import com.askapp.audit.ratelimit.TransactionalRequestExecutor;
import com.askapp.audit.repository.AuditLogRepository;
import com.askapp.audit.service.DemoDataGenerator;
import com.askapp.common.ratelimit.RateLimitProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DemoDataControllerTest {

	private AuditLogRepository repository;
	private DemoDataController controller;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		repository = mock(AuditLogRepository.class);
		when(repository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
		TransactionalRequestExecutor txExecutor = mock(TransactionalRequestExecutor.class);
		when(txExecutor.run(any())).thenAnswer(invocation -> {
			Supplier<?> work = invocation.getArgument(0);
			return work.get();
		});
		controller = new DemoDataController(repository, new DemoDataGenerator(), txExecutor);
		// Standalone MockMvc so @Valid and the advice run without a full Spring context —
		// plain method calls can't exercise body validation.
		mockMvc = MockMvcBuilders.standaloneSetup(controller)
			.setControllerAdvice(new GlobalExceptionHandler(new RateLimitProperties()))
			.build();
	}

	@Test
	void generate_insertsTheRequestedNumberOfRows() {
		DemoDataResponse response = controller.generate(new DemoDataRequest(25));

		assertThat(response.created()).isEqualTo(25);
		verify(repository).saveAll(argThat((List<AuditLog> rows) -> rows.size() == 25));
	}

	@Test
	void generate_returns201WithTheCreatedCount() throws Exception {
		mockMvc.perform(post("/api/v1/audit-logs/demo")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"count\": 3}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.created").value(3));
	}

	@Test
	void generate_rejectsAnOutOfRangeCountWith400NotA500() throws Exception {
		mockMvc.perform(post("/api/v1/audit-logs/demo")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"count\": 0}"))
			.andExpect(status().isBadRequest());

		mockMvc.perform(post("/api/v1/audit-logs/demo")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"count\": 501}"))
			.andExpect(status().isBadRequest());

		// A missing count deserializes to 0 and must fail the same way, not insert zero rows.
		mockMvc.perform(post("/api/v1/audit-logs/demo")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isBadRequest());
	}

}
