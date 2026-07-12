import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  AuditLog,
  AuditLogFilter,
  AuditLogStats,
  AuditLogTimeBucket,
  DemoDataResponse,
  FeaturesResponse,
  PageRequest,
  PagedResponse,
  TimelineInterval,
} from './audit.models';

/** Calls the Audit service's paginated search and aggregation endpoints. */
@Injectable({ providedIn: 'root' })
export class AuditService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.auditApiUrl}/api/v1/audit-logs`;
  private readonly metaBase = `${environment.auditApiUrl}/api/v1/meta`;

  /** Runtime capability flags — e.g. whether the demo-log generator exists on this backend. */
  features(): Observable<FeaturesResponse> {
    return this.http.get<FeaturesResponse>(`${this.metaBase}/features`);
  }

  search(filter: AuditLogFilter, page: PageRequest): Observable<PagedResponse<AuditLog>> {
    const params = this.filterParams(filter)
      .set('page', page.page)
      .set('size', page.size)
      .set('sort', page.sort);
    return this.http.get<PagedResponse<AuditLog>>(`${this.base}/search`, { params });
  }

  stats(filter: AuditLogFilter): Observable<AuditLogStats> {
    return this.http.get<AuditLogStats>(`${this.base}/stats`, {
      params: this.filterParams(filter),
    });
  }

  /** Events-over-time buckets, same filters as /search plus the bucket granularity. */
  timeline(filter: AuditLogFilter, interval: TimelineInterval): Observable<AuditLogTimeBucket[]> {
    return this.http.get<AuditLogTimeBucket[]>(`${this.base}/stats/timeline`, {
      params: this.filterParams(filter).set('interval', interval),
    });
  }

  /** Bulk-insert generated demo rows (backend endpoint exists in LOCAL/DEV only). */
  addDemoLogs(count: number): Observable<DemoDataResponse> {
    return this.http.post<DemoDataResponse>(`${this.base}/demo`, { count });
  }

  private filterParams(filter: AuditLogFilter): HttpParams {
    let params = new HttpParams();
    if (filter.entityType) params = params.set('entityType', filter.entityType);
    if (filter.action) params = params.set('action', filter.action);
    if (filter.details) params = params.set('details', filter.details);
    if (filter.from) params = params.set('from', filter.from);
    if (filter.to) params = params.set('to', filter.to);
    if (filter.includeDeleted) params = params.set('includeDeleted', true);
    return params;
  }
}
