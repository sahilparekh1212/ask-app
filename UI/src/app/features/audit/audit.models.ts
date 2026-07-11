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
