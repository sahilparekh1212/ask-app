package com.askapp.audit.dto;

/**
 * Immutable filter criteria for the security-master listing. Any field may be {@code null},
 * meaning "no constraint on this dimension". Kept as a record so the same instance can be logged
 * and handed to {@code SecurityMasterSpecifications}, mirroring {@link AuditLogFilter}.
 *
 * @param assetClass exact-match on the asset class (EQUITY/BOND/…), or null for any
 * @param currency   exact-match on the ISO currency code, or null for any
 * @param name       case-insensitive substring match on the name, or null
 */
public record SecurityFilter(String assetClass, String currency, String name) {
}
