package com.aisandbox.audit.repository;

import com.aisandbox.audit.dto.AuditLogFilter;
import com.aisandbox.audit.model.AuditLog;
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
			if (filter.from() != null) {
				predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), filter.from()));
			}
			if (filter.to() != null) {
				predicates.add(cb.lessThan(root.get("createdAt"), filter.to()));
			}
			return cb.and(predicates.toArray(Predicate[]::new));
		};
	}
}
