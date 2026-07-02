package com.aisandbox.audit.controller;

import com.aisandbox.audit.model.AuditLog;
import com.aisandbox.audit.repository.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the {@code @PreAuthorize("hasRole('ADMIN')")} gate on {@code DELETE
 * /api/v1/audit-logs/{id}} is actually enforced end-to-end (method-security AOP only kicks in
 * behind a real Spring proxy, so {@link AuditLogControllerTest}'s plain {@code new
 * AuditLogController(...)} unit tests can't exercise it).
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuditLogControllerSecurityTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AuditLogRepository auditLogRepository;

	@Test
	void deleteIsRejectedWithoutTheAdminRole() throws Exception {
		AuditLog saved = auditLogRepository.save(new AuditLog("User", "CREATE", "details"));

		mockMvc.perform(delete("/api/v1/audit-logs/" + saved.getId())
				.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
			.andExpect(status().isForbidden());
	}

	@Test
	void deleteSucceedsWithTheAdminRole() throws Exception {
		AuditLog saved = auditLogRepository.save(new AuditLog("User", "CREATE", "details"));

		mockMvc.perform(delete("/api/v1/audit-logs/" + saved.getId())
				.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
			.andExpect(status().isNoContent());
	}

}
