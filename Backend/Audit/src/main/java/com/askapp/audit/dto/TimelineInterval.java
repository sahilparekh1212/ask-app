package com.askapp.audit.dto;

/**
 * Granularity of the events-over-time aggregation. The enum whitelists what may
 * reach the database-side {@code date_trunc} call — the request param is
 * converted to this type, so an arbitrary string can never become part of the
 * grouping expression.
 */
public enum TimelineInterval {

	HOUR,
	DAY;

	/**
	 * Case-insensitive parse — the API documents {@code interval=hour|day}, but Spring's
	 * default {@code @RequestParam} enum binding is {@code Enum.valueOf} (case-sensitive)
	 * and would 400 the documented lowercase form. Registered as the String converter in
	 * {@code WebConfig}; an unknown value still throws and surfaces as a 400.
	 */
	public static TimelineInterval from(String value) {
		return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
	}

	/** The {@code date_trunc} field name for this granularity. */
	public String unit() {
		return name().toLowerCase(java.util.Locale.ROOT);
	}
}
