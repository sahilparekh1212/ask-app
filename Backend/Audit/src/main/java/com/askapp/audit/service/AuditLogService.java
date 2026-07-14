package com.askapp.audit.service;

import com.askapp.audit.dto.AuditLogCount;
import com.askapp.audit.dto.AuditLogFilter;
import com.askapp.audit.dto.AuditLogStats;
import com.askapp.audit.dto.AuditLogTimeBucket;
import com.askapp.audit.dto.TimelineInterval;
import com.askapp.audit.model.AuditLog;
import com.askapp.audit.repository.AuditLogRepository;
import com.askapp.audit.repository.AuditLogSpecifications;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Read-side query and aggregation logic for audit logs.
 *
 * <p>Both operations build a single {@link Specification} from the
 * {@link AuditLogFilter} so the paginated rows and the grouped statistics always
 * agree on what "matching" means. The aggregation reuses that same Specification
 * inside a grouped Criteria query rather than re-listing rows in memory, so the
 * counts are computed in the database and ride the same indexes as the search.
 */
@Service
public class AuditLogService {

	/** Sort properties a client is allowed to order by; anything else is ignored. */
	private static final Set<String> SORTABLE = Set.of("createdAt", "entityType", "action", "id");

	private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

	private final AuditLogRepository repository;
	private final EntityManager entityManager;

	public AuditLogService(AuditLogRepository repository, EntityManager entityManager) {
		this.repository = repository;
		this.entityManager = entityManager;
	}

	/** Paginated, filtered, safely-sorted page of audit logs. */
	@Transactional(readOnly = true)
	public Page<AuditLog> search(AuditLogFilter filter, Pageable pageable) {
		return repository.findAll(AuditLogSpecifications.matching(filter), sanitize(pageable));
	}

	/** Total count plus per-action and per-entityType breakdowns for the filter. */
	@Transactional(readOnly = true)
	public AuditLogStats aggregate(AuditLogFilter filter) {
		Specification<AuditLog> spec = AuditLogSpecifications.matching(filter);
		long total = repository.count(spec);
		return new AuditLogStats(total, groupedCount(filter, "action"), groupedCount(filter, "entityType"));
	}

	/**
	 * Events-over-time: database-side {@code GROUP BY date_trunc(interval, createdAt)}
	 * with the filter applied, ordered chronologically. Reuses the same
	 * {@link AuditLogSpecifications} as {@link #search}/{@link #aggregate}, so the
	 * trend line counts exactly the rows the table shows. {@code date_trunc} exists
	 * natively on both PostgreSQL (prod) and H2 (tests), and the unit string comes
	 * from the {@link TimelineInterval} whitelist, never from request text. Bucket
	 * boundaries follow the database session's time zone (the JVM default — UTC in
	 * the prod containers). Empty buckets are simply absent — zero-filling is a
	 * presentation concern, and the client should anchor its axis on the returned
	 * bucket values rather than assume UTC boundaries.
	 */
	@Transactional(readOnly = true)
	public List<AuditLogTimeBucket> timeline(AuditLogFilter filter, TimelineInterval interval) {
		CriteriaBuilder cb = entityManager.getCriteriaBuilder();
		CriteriaQuery<AuditLogTimeBucket> query = cb.createQuery(AuditLogTimeBucket.class);
		Root<AuditLog> root = query.from(AuditLog.class);

		Expression<Instant> bucket = cb.function("date_trunc", Instant.class,
			cb.literal(interval.unit()), root.get("createdAt"));
		Predicate where = AuditLogSpecifications.matching(filter).toPredicate(root, query, cb);

		query.select(cb.construct(AuditLogTimeBucket.class, bucket, cb.count(root)));
		if (where != null) {
			query.where(where);
		}
		query.groupBy(bucket).orderBy(cb.asc(bucket));

		return entityManager.createQuery(query).getResultList();
	}

	/**
	 * Database-side {@code GROUP BY groupField} with the filter applied, ordered by
	 * descending count. Reuses {@link AuditLogSpecifications} so the WHERE clause is
	 * identical to {@link #search}.
	 */
	private List<AuditLogCount> groupedCount(AuditLogFilter filter, String groupField) {
		CriteriaBuilder cb = entityManager.getCriteriaBuilder();
		CriteriaQuery<AuditLogCount> query = cb.createQuery(AuditLogCount.class);
		Root<AuditLog> root = query.from(AuditLog.class);

		Path<String> group = root.get(groupField);
		Expression<Long> count = cb.count(root);
		Predicate where = AuditLogSpecifications.matching(filter).toPredicate(root, query, cb);

		query.select(cb.construct(AuditLogCount.class, group, count));
		if (where != null) {
			query.where(where);
		}
		query.groupBy(group).orderBy(cb.desc(count));

		return entityManager.createQuery(query).getResultList();
	}

	/**
	 * Drop any sort the caller requested on a non-whitelisted property (prevents
	 * 500s / unindexed scans from arbitrary {@code sort=} params) and fall back to
	 * newest-first when nothing valid remains.
	 */
	private Pageable sanitize(Pageable pageable) {
		Sort requested = Sort.by(pageable.getSort().stream()
			.filter(order -> SORTABLE.contains(order.getProperty()))
			.toList());
		Sort effective = requested.isSorted() ? requested : DEFAULT_SORT;
		return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), effective);
	}
}
