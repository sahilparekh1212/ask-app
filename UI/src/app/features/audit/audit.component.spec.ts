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
