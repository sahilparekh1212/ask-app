package com.askapp.audit.repository;

import com.askapp.audit.dto.SecurityFilter;
import com.askapp.audit.model.SecurityMaster;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the {@link Specification} for a {@link SecurityFilter}, mirroring
 * {@link AuditLogSpecifications}: equality on {@code assetClass}/{@code currency} and a
 * case-insensitive substring match on {@code name}.
 */
public final class SecurityMasterSpecifications {

	private SecurityMasterSpecifications() {
	}

	public static Specification<SecurityMaster> matching(SecurityFilter filter) {
		return (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (StringUtils.hasText(filter.assetClass())) {
				predicates.add(cb.equal(root.get("assetClass"), filter.assetClass()));
			}
			if (StringUtils.hasText(filter.currency())) {
				predicates.add(cb.equal(root.get("currency"), filter.currency()));
			}
			if (StringUtils.hasText(filter.name())) {
				predicates.add(cb.like(cb.lower(root.get("name")),
					"%" + escapeLike(filter.name().toLowerCase()) + "%", '\\'));
			}
			return cb.and(predicates.toArray(Predicate[]::new));
		};
	}

	/** Escape LIKE metacharacters so a literal {@code %}, {@code _} or {@code \} isn't a wildcard. */
	private static String escapeLike(String value) {
		return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}
}
