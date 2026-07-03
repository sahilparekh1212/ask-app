import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { AuditService } from './audit.service';
import { environment } from '../../../environments/environment';

describe('AuditService', () => {
  let service: AuditService;
  let httpMock: HttpTestingController;
  const base = `${environment.auditApiUrl}/api/v1/audit-logs`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AuditService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('builds a search request with paging, sort and only the set filters', () => {
    service
      .search({ entityType: 'User', action: null }, { page: 2, size: 20, sort: 'createdAt,desc' })
      .subscribe();

    const req = httpMock.expectOne((r) => r.url === `${base}/search`);
    expect(req.request.params.get('entityType')).toBe('User');
    expect(req.request.params.has('action')).toBeFalse();
    expect(req.request.params.get('page')).toBe('2');
    expect(req.request.params.get('sort')).toBe('createdAt,desc');
    req.flush({ content: [], page: 2, size: 20, totalElements: 0, totalPages: 0, last: true });
  });

  it('requests stats with the same filter params', () => {
    service.stats({ action: 'LOGIN' }).subscribe();
    const req = httpMock.expectOne((r) => r.url === `${base}/stats`);
    expect(req.request.params.get('action')).toBe('LOGIN');
    req.flush({ total: 0, byAction: [], byEntityType: [] });
  });

  it('posts the demo count to the demo endpoint', () => {
    service.addDemoLogs(7).subscribe();
    const req = httpMock.expectOne(`${base}/demo`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ count: 7 });
    req.flush({ created: 7 });
  });

  it('sends the details substring filter and omits it when unset', () => {
    service
      .search({ details: 'report' }, { page: 0, size: 20, sort: 'createdAt,desc' })
      .subscribe();
    const withDetails = httpMock.expectOne((r) => r.url === `${base}/search`);
    expect(withDetails.request.params.get('details')).toBe('report');
    withDetails.flush({
      content: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
      last: true,
    });

    service.search({}, { page: 0, size: 20, sort: 'createdAt,desc' }).subscribe();
    const without = httpMock.expectOne((r) => r.url === `${base}/search`);
    expect(without.request.params.has('details')).toBeFalse();
    without.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, last: true });
  });
});
