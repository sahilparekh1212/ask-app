import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { AuditComponent } from './audit.component';
import { environment } from '../../../environments/environment';

describe('AuditComponent', () => {
  let component: AuditComponent;
  let fixture: ComponentFixture<AuditComponent>;
  let httpMock: HttpTestingController;
  const base = `${environment.auditApiUrl}/api/v1/audit-logs`;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AuditComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(AuditComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  function flushInitialLoad(): void {
    fixture.detectChanges(); // ngOnInit → search + stats
    httpMock
      .expectOne((r) => r.url === `${base}/search`)
      .flush({
        content: [
          {
            id: 1,
            entityType: 'User',
            action: 'LOGIN',
            details: null,
            eventId: 'e1',
            deleted: false,
            createdAt: '2026-01-01T00:00:00Z',
            updatedAt: '2026-01-01T00:00:00Z',
          },
        ],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
        last: true,
      });
    httpMock
      .expectOne((r) => r.url === `${base}/stats`)
      .flush({
        total: 1,
        byAction: [{ key: 'LOGIN', count: 1 }],
        byEntityType: [{ key: 'User', count: 1 }],
      });
  }

  it('should create and load the first page on init', () => {
    flushInitialLoad();
    expect(component).toBeTruthy();
    expect(component.result()?.content.length).toBe(1);
    expect(component.stats()?.total).toBe(1);
  });

  it('populates the dropdown options from the stats buckets and never shrinks them', () => {
    flushInitialLoad();
    expect(component.entityTypeOptions()).toEqual(['User']);
    expect(component.actionOptions()).toEqual(['LOGIN']);

    // A narrower stats response (filter applied) adds new keys but drops none.
    component.applyFilters();
    httpMock
      .expectOne((r) => r.url === `${base}/search`)
      .flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, last: true });
    httpMock
      .expectOne((r) => r.url === `${base}/stats`)
      .flush({
        total: 1,
        byAction: [{ key: 'CREATE', count: 1 }],
        byEntityType: [{ key: 'Order', count: 1 }],
      });

    expect(component.entityTypeOptions()).toEqual(['Order', 'User']);
    expect(component.actionOptions()).toEqual(['CREATE', 'LOGIN']);
  });

  it('adds demo logs then reloads the table and stats', () => {
    flushInitialLoad();
    component.demoCount.setValue(5);
    component.addDemoLogs();

    const demo = httpMock.expectOne((r) => r.url === `${base}/demo`);
    expect(demo.request.method).toBe('POST');
    expect(demo.request.body).toEqual({ count: 5 });
    demo.flush({ created: 5 });

    // Success triggers a reload so the new rows appear immediately.
    httpMock
      .expectOne((r) => r.url === `${base}/search`)
      .flush({ content: [], page: 0, size: 20, totalElements: 5, totalPages: 1, last: true });
    httpMock
      .expectOne((r) => r.url === `${base}/stats`)
      .flush({ total: 5, byAction: [], byEntityType: [] });

    expect(component.demoMessage()).toBe('Added 5 demo logs.');
    expect(component.demoBusy()).toBeFalse();
  });

  it('rejects an out-of-range demo count without calling the backend', () => {
    flushInitialLoad();
    component.demoCount.setValue(0);
    component.addDemoLogs();

    expect(component.demoMessage()).toBe('Count must be between 1 and 500.');
    // afterEach's httpMock.verify() would fail on any stray /demo request.
  });

  it('sends the details filter as a query param when set', () => {
    flushInitialLoad();
    component.filterForm.patchValue({ details: '  sales report ' });
    component.applyFilters();

    const search = httpMock.expectOne((r) => r.url === `${base}/search`);
    expect(search.request.params.get('details')).toBe('sales report'); // trimmed
    search.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, last: true });

    const stats = httpMock.expectOne((r) => r.url === `${base}/stats`);
    expect(stats.request.params.get('details')).toBe('sales report');
    stats.flush({ total: 0, byAction: [], byEntityType: [] });
  });

  it('toggles sort direction when the same column is clicked twice', () => {
    flushInitialLoad();
    component.sortBy('createdAt'); // was default createdAt/desc → asc
    expect(component.sortField()).toBe('createdAt');
    expect(component.sortDir()).toBe('asc');
    // that click reloads → flush the follow-up requests
    httpMock
      .expectOne((r) => r.url === `${base}/search`)
      .flush({
        content: [],
        page: 0,
        size: 20,
        totalElements: 0,
        totalPages: 0,
        last: true,
      });
    httpMock
      .expectOne((r) => r.url === `${base}/stats`)
      .flush({ total: 0, byAction: [], byEntityType: [] });
  });
});
