package com.aisandbox.audit.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Stable, serialization-safe wrapper for a page of results.
 *
 * <p>Spring's {@code Page}/{@code PageImpl} is deliberately not returned directly:
 * its JSON shape is an internal contract that Spring warns may change between
 * versions. Exposing this explicit record keeps the public API contract under our
 * control and documents exactly which paging fields clients can rely on.
 *
 * @param content       the rows on this page
 * @param page          zero-based page index
 * @param size          requested page size
 * @param totalElements total rows across all pages matching the query
 * @param totalPages    total number of pages
 * @param last          true if this is the final page
 */
public record PagedResponse<T>(
	List<T> content,
	int page,
	int size,
	long totalElements,
	int totalPages,
	boolean last
) {

	public static <T> PagedResponse<T> from(Page<T> page) {
		return new PagedResponse<>(
			page.getContent(),
			page.getNumber(),
			page.getSize(),
			page.getTotalElements(),
			page.getTotalPages(),
			page.isLast()
		);
	}
}
