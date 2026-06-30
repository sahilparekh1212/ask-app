package com.aisandbox.audit.service;

import com.aisandbox.audit.config.JpaAuditingConfig;
import com.aisandbox.audit.dto.AuditLogCount;
import com.aisandbox.audit.dto.AuditLogFilter;
import com.aisandbox.audit.dto.AuditLogStats;
import com.aisandbox.audit.model.AuditLog;
import com.aisandbox.audit.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({AuditLogService.class, JpaAuditingConfig.class})
class AuditLogServiceIntegrationTest {

	@Autowired
	private AuditLogService service;

	@Autowired
	private AuditLogRepository repository;

	@BeforeEach
	void seed() {
		repository.deleteAll();
		repository.save(new AuditLog("User", "CREATE", "u1"));
		repository.save(new AuditLog("User", "CREATE", "u2"));
		repository.save(new AuditLog("User", "UPDATE", "u3"));
		repository.save(new AuditLog("Order", "CREATE", "o1"));
		AuditLog removed = new AuditLog("Order", "DELETE", "o2");
		removed.setDeleted(true);
		repository.save(removed);
		repository.flush();
	}

	private AuditLogFilter filter(String entityType, String action, Instant from, Instant to, boolean includeDeleted) {
		return new AuditLogFilter(entityType, action, from, to, includeDeleted);
	}

	@Test
	void search_excludesSoftDeletedByDefault() {
		Page<AuditLog> page = service.search(filter(null, null, null, null, false), PageRequest.of(0, 20));

		assertThat(page.getTotalElements()).isEqualTo(4);
		assertThat(page.getContent()).noneMatch(AuditLog::isDeleted);
	}

	@Test
	void search_includesSoftDeletedWhenRequested() {
		Page<AuditLog> page = service.search(filter(null, null, null, null, true), PageRequest.of(0, 20));

		assertThat(page.getTotalElements()).isEqualTo(5);
	}

	@Test
	void search_filtersByEntityTypeAndAction() {
		Page<AuditLog> page = service.search(filter("User", "CREATE", null, null, false), PageRequest.of(0, 20));

		assertThat(page.getTotalElements()).isEqualTo(2);
		assertThat(page.getContent())
			.allSatisfy(log -> {
				assertThat(log.getEntityType()).isEqualTo("User");
				assertThat(log.getAction()).isEqualTo("CREATE");
			});
	}

	@Test
	void search_paginatesResults() {
		PageRequest firstPage = PageRequest.of(0, 2, Sort.by("id"));
		PageRequest secondPage = PageRequest.of(1, 2, Sort.by("id"));

		Page<AuditLog> page0 = service.search(filter(null, null, null, null, false), firstPage);
		Page<AuditLog> page1 = service.search(filter(null, null, null, null, false), secondPage);

		assertThat(page0.getContent()).hasSize(2);
		assertThat(page0.getTotalElements()).isEqualTo(4);
		assertThat(page0.getTotalPages()).isEqualTo(2);
		assertThat(page0.isLast()).isFalse();
		assertThat(page1.getContent()).hasSize(2);
		assertThat(page1.isLast()).isTrue();
		assertThat(page0.getContent()).doesNotContainAnyElementsOf(page1.getContent());
	}

	@Test
	void search_sortsByWhitelistedProperty() {
		Page<AuditLog> page = service.search(
			filter(null, null, null, null, false),
			PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "action")));

		assertThat(page.getContent())
			.extracting(AuditLog::getAction)
			.containsExactly("CREATE", "CREATE", "CREATE", "UPDATE");
	}

	@Test
	void search_ignoresNonWhitelistedSortAndDoesNotThrow() {
		// "details" is not a permitted sort property — must be dropped, not 500.
		Page<AuditLog> page = service.search(
			filter(null, null, null, null, false),
			PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "details")));

		assertThat(page.getTotalElements()).isEqualTo(4);
	}

	@Test
	void search_appliesCreatedAtLowerBound() {
		Instant future = Instant.now().plus(1, ChronoUnit.DAYS);

		Page<AuditLog> page = service.search(filter(null, null, future, null, false), PageRequest.of(0, 20));

		assertThat(page.getTotalElements()).isZero();
	}

	@Test
	void search_appliesCreatedAtUpperBound() {
		Instant past = Instant.now().minus(1, ChronoUnit.DAYS);

		Page<AuditLog> page = service.search(filter(null, null, null, past, false), PageRequest.of(0, 20));

		assertThat(page.getTotalElements()).isZero();
	}

	@Test
	void search_returnsAllWithinAWideCreatedAtRange() {
		Instant from = Instant.now().minus(1, ChronoUnit.DAYS);
		Instant to = Instant.now().plus(1, ChronoUnit.DAYS);

		Page<AuditLog> page = service.search(filter(null, null, from, to, false), PageRequest.of(0, 20));

		assertThat(page.getTotalElements()).isEqualTo(4);
	}

	@Test
	void aggregate_groupsByActionAndEntityTypeOrderedByCountDesc() {
		AuditLogStats stats = service.aggregate(filter(null, null, null, null, false));

		assertThat(stats.total()).isEqualTo(4);
		assertThat(stats.byAction())
			.containsExactly(new AuditLogCount("CREATE", 3), new AuditLogCount("UPDATE", 1));
		assertThat(stats.byEntityType())
			.containsExactly(new AuditLogCount("User", 3), new AuditLogCount("Order", 1));
	}

	@Test
	void aggregate_honoursTheSameFilterAsSearch() {
		AuditLogStats stats = service.aggregate(filter("User", null, null, null, false));

		assertThat(stats.total()).isEqualTo(3);
		assertThat(stats.byEntityType()).containsExactly(new AuditLogCount("User", 3));
		assertThat(stats.byAction())
			.containsExactlyInAnyOrder(new AuditLogCount("CREATE", 2), new AuditLogCount("UPDATE", 1));
	}

	@Test
	void aggregate_includesSoftDeletedWhenRequested() {
		AuditLogStats stats = service.aggregate(filter(null, null, null, null, true));

		assertThat(stats.total()).isEqualTo(5);
		assertThat(stats.byAction()).contains(new AuditLogCount("DELETE", 1));
		List<AuditLogCount> entityCounts = stats.byEntityType();
		assertThat(entityCounts).contains(new AuditLogCount("User", 3), new AuditLogCount("Order", 2));
	}
}
