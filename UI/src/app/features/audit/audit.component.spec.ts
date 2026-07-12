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
    fixture.detectChanges(); // ngOnInit → search + stats + KPIs
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
    filteredStats().flush({
      total: 1,
      byAction: [{ key: 'LOGIN', count: 1 }],
      byEntityType: [{ key: 'User', count: 1 }],
    });
    flushTimeline();
    flushKpis();
    // ngOnInit also probes the capability endpoint to decide whether to show the demo button.
    httpMock
      .expectOne((r) => r.url === `${environment.auditApiUrl}/api/v1/meta/features`)
      .flush({ demoData: true });
  }

  function flushTimeline(buckets: { bucket: string; count: number }[] = []): void {
    httpMock.expectOne((r) => r.url === `${base}/stats/timeline`).flush(buckets);
  }

  /** The panel's own stats request — the KPI requests carry a `from` param, this one doesn't. */
  function filteredStats() {
    return httpMock.expectOne((r) => r.url === `${base}/stats` && !r.params.has('from'));
  }

  const emptyStats = { total: 0, byAction: [], byEntityType: [] };

  function flushKpis(
    day: { total: number; byAction: unknown[]; byEntityType: unknown[] } = emptyStats,
    blocked = emptyStats,
    errored = emptyStats,
  ): void {
    httpMock
      .expectOne(
        (r) => r.url === `${base}/stats` && r.params.has('from') && !r.params.has('details'),
      )
      .flush(day);
    httpMock
      .expectOne((r) => r.url === `${base}/stats` && r.params.get('details') === 'blocked=true')
      .flush(blocked);
    httpMock
      .expectOne((r) => r.url === `${base}/stats` && r.params.get('details') === 'error=')
      .flush(errored);
  }

  it('should create and load the first page on init', () => {
    flushInitialLoad();
    expect(component).toBeTruthy();
    expect(component.result()?.content.length).toBe(1);
    expect(component.stats()?.total).toBe(1);
  });

  it('hides the demo-log button when the backend reports demoData:false (a PROD backend)', () => {
    fixture.detectChanges(); // ngOnInit → search + stats + features
    httpMock
      .expectOne((r) => r.url === `${base}/search`)
      .flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, last: true });
    filteredStats().flush(emptyStats);
    flushTimeline();
    flushKpis();
    httpMock
      .expectOne((r) => r.url === `${environment.auditApiUrl}/api/v1/meta/features`)
      .flush({ demoData: false });
    fixture.detectChanges();

    expect(component.demoAvailable()).toBeFalse();
    expect((fixture.nativeElement as HTMLElement).querySelector('.demo-tools')).toBeNull();
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
    flushTimeline();

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

    // Success triggers a reload (and a KPI refresh) so the new rows appear immediately.
    httpMock
      .expectOne((r) => r.url === `${base}/search`)
      .flush({ content: [], page: 0, size: 20, totalElements: 5, totalPages: 1, last: true });
    filteredStats().flush({ total: 5, byAction: [], byEntityType: [] });
    flushTimeline();
    flushKpis();

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

    // The timeline honours the same filters as the table and the other charts.
    const timeline = httpMock.expectOne((r) => r.url === `${base}/stats/timeline`);
    expect(timeline.request.params.get('details')).toBe('sales report');
    timeline.flush([]);
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
    flushTimeline();
  });

  it('zero-fills the timeline axis between and around the returned buckets', () => {
    // Anchor mid-slot (…:30) so the ~ms between the test's "now" and the component's
    // "now" can never flip a bar across the window boundary.
    const anchor = Date.now() - 3.5 * 3_600_000;
    fixture.detectChanges();
    httpMock
      .expectOne((r) => r.url === `${base}/search`)
      .flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, last: true });
    filteredStats().flush({ total: 3, byAction: [], byEntityType: [] });
    flushKpis();
    flushTimeline([
      { bucket: new Date(anchor).toISOString(), count: 2 },
      { bucket: new Date(anchor + 2 * 3_600_000).toISOString(), count: 1 },
    ]);
    httpMock
      .expectOne((r) => r.url === `${environment.auditApiUrl}/api/v1/meta/features`)
      .flush({ demoData: false });

    const bars = component.timelineBars();
    // 24h window on an hourly grid anchored 3.5h ago: 21 slots back + 4 forward.
    expect(bars.length).toBe(25);
    expect(bars[21].count).toBe(2); // the anchor bucket
    expect(bars[22].count).toBe(0); // the gap hour, zero-filled
    expect(bars[23].count).toBe(1); // the second bucket
    expect(component.maxTimelineCount()).toBe(2);
  });

  it('computes the KPI cards from the 24h aggregations', () => {
    fixture.detectChanges();
    httpMock
      .expectOne((r) => r.url === `${base}/search`)
      .flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, last: true });
    filteredStats().flush(emptyStats);
    flushTimeline();
    flushKpis(
      {
        total: 40,
        byAction: [
          { key: 'CHAT', count: 25 },
          { key: 'LOGIN', count: 10 },
          { key: 'SEARCH', count: 5 },
        ],
        byEntityType: [
          { key: 'Assistant', count: 25 },
          { key: 'User', count: 15 },
        ],
      },
      { total: 3, byAction: [], byEntityType: [] },
      { total: 1, byAction: [], byEntityType: [] },
    );
    httpMock
      .expectOne((r) => r.url === `${environment.auditApiUrl}/api/v1/meta/features`)
      .flush({ demoData: false });

    const k = component.kpis();
    expect(k?.total).toBe(40);
    expect(k?.busiest).toBe('Assistant'); // top byEntityType bucket
    expect(k?.eventTypes).toBe(3); // distinct actions
    expect(k?.blockedRate).toBeCloseTo(0.1); // (3 blocked + 1 errored) / 40
    expect(component.blockedRateLabel(k!)).toBe('10%');
  });

  it('refetches with day granularity when the 7d window is selected', () => {
    flushInitialLoad();

    component.setTimelineWindow('7d');

    const req = httpMock.expectOne((r) => r.url === `${base}/stats/timeline`);
    expect(req.request.params.get('interval')).toBe('day');
    req.flush([]);
    expect(component.timelineWindow()).toBe('7d');
  });
});
