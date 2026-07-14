package com.askapp.audit.repository;

import com.askapp.audit.dto.AuditLogFilter;
import com.askapp.audit.model.AuditLog;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the {@link Specification} for an {@link AuditLogFilter}.
 *
 * <p>The same predicate set is reused by the paginated search (via
 * {@code JpaSpecificationExecutor}) and by the grouped aggregation (which applies
 * {@link #toPredicate} against its own grouped {@code CriteriaQuery}), so the two
 * endpoints can never drift out of sync on what "matching" means.
 */
public final class AuditLogSpecifications {

	private AuditLogSpecifications() {
	}

	public static Specification<AuditLog> matching(AuditLogFilter filter) {
		return (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (!filter.includeDeleted()) {
				predicates.add(cb.isFalse(root.get("deleted")));
			}
			if (StringUtils.hasText(filter.entityType())) {
				predicates.add(cb.equal(root.get("entityType"), filter.entityType()));
			}
			if (StringUtils.hasText(filter.action())) {
				predicates.add(cb.equal(root.get("action"), filter.action()));
			}
			if (StringUtils.hasText(filter.details())) {
				predicates.add(cb.like(cb.lower(root.get("details")),
					"%" + escapeLike(filter.details().toLowerCase()) + "%", '\\'));
			}
			if (filter.from() != null) {
				predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), filter.from()));
			}
			if (filter.to() != null) {
				predicates.add(cb.lessThan(root.get("createdAt"), filter.to()));
			}
			return cb.and(predicates.toArray(Predicate[]::new));
		};
	}

	/**
	 * A user searching for a literal {@code %}, {@code _} or {@code \} must not have it
	 * act as a LIKE wildcard, so escape them with the explicit escape character above.
	 */
	private static String escapeLike(String value) {
		return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}
}
