import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { FeatureFlagService } from './feature-flag.service';
import { AuthService } from '../auth/auth.service';
import { environment } from '../../../environments/environment';

describe('FeatureFlagService', () => {
  let service: FeatureFlagService;
  let httpMock: HttpTestingController;
  const url = `${environment.auditApiUrl}/api/v1/meta/flags`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        // Keep the auth-effect quiet so we drive load() explicitly (not authenticated → no fetch).
        { provide: AuthService, useValue: { isAuthenticated: signal(false) } },
      ],
    });
    service = TestBed.inject(FeatureFlagService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('loads the flag list and folds it into enabled/disabled lookups', () => {
    service.load().subscribe();

    const req = httpMock.expectOne(url);
    expect(req.request.method).toBe('GET');
    req.flush([
      { key: 'chat', enabled: true, description: 'Chat' },
      { key: 'voice', enabled: false, description: 'Voice' },
    ]);

    expect(service.isEnabled('chat')).toBeTrue();
    expect(service.isEnabled('voice')).toBeFalse();
    expect(service.loaded()).toBeTrue();
  });

  it('fails open before any load has happened', () => {
    expect(service.isEnabled('chat')).toBeTrue();
    expect(service.loaded()).toBeFalse();
  });

  it('fails open for an unknown flag key after loading', () => {
    service.load().subscribe();
    httpMock.expectOne(url).flush([{ key: 'chat', enabled: true, description: 'Chat' }]);

    expect(service.isEnabled('does-not-exist')).toBeTrue();
  });

  it('fails open when the request errors', () => {
    service.load().subscribe({ error: () => undefined });
    httpMock.expectOne(url).flush('boom', { status: 500, statusText: 'Server Error' });

    expect(service.isEnabled('voice')).toBeTrue();
    expect(service.loaded()).toBeFalse();
  });
});
