/** A single audit-log row (GET /api/v1/audit-logs/search content items). */
export interface AuditLog {
  id: number;
  entityType: string;
  action: string;
  details: string | null;
  eventId: string | null;
  deleted: boolean;
  createdAt: string; // ISO-8601 instant
  updatedAt: string;
}

/** Backend PagedResponse<T> — a stable paging envelope (not Spring's Page shape). */
export interface PagedResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}

/** One grouped-count bucket from /stats. */
export interface AuditLogCount {
  key: string;
  count: number;
}

/** Aggregated counts over the same filter as /search. */
export interface AuditLogStats {
  total: number;
  byAction: AuditLogCount[];
  byEntityType: AuditLogCount[];
}

/** One events-over-time bucket from /stats/timeline. Empty buckets are omitted by the API. */
export interface AuditLogTimeBucket {
  bucket: string; // ISO instant of the bucket start (server-side date_trunc boundary)
  count: number;
}

/** Granularity of the /stats/timeline aggregation. */
export type TimelineInterval = 'hour' | 'day';

/** Filters shared by /search and /stats. */
export interface AuditLogFilter {
  entityType?: string | null;
  action?: string | null;
  details?: string | null; // case-insensitive substring match
  from?: string | null; // ISO instant
  to?: string | null;
  includeDeleted?: boolean;
}

/** Result of POST /demo — how many generated rows were inserted. */
export interface DemoDataResponse {
  created: number;
}

/** Runtime capability flags (GET /api/v1/meta/features) the SPA uses to adapt its UI. */
export interface FeaturesResponse {
  demoData: boolean; // demo-log generation exists only on a LOCAL/DEV backend
}

/** Zero-based page request plus a Spring `sort=field,dir` selection. */
export interface PageRequest {
  page: number;
  size: number;
  sort: string; // e.g. "createdAt,desc"
}

/** A security-master reference-data row (GET /api/v1/refdata/securities content items). */
export interface SecurityMaster {
  id: number;
  instrumentId: string;
  isin: string | null;
  cusip: string | null;
  sedol: string | null;
  name: string | null;
  assetClass: string | null;
  currency: string | null;
  price: number | null;
  asOfDate: string | null; // ISO date (yyyy-MM-dd)
}

/** Filters for the reference-data listing (GET /api/v1/refdata/securities). */
export interface SecurityFilter {
  assetClass?: string | null;
  currency?: string | null;
  name?: string | null;
}
