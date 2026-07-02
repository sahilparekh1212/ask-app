import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';

import { authInterceptor } from './auth.interceptor';
import { TokenStorageService } from './token-storage.service';
import { environment } from '../../../environments/environment';

describe('authInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let storage: TokenStorageService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    });
    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
    storage = TestBed.inject(TokenStorageService);
    storage.clear();
  });

  afterEach(() => {
    httpMock.verify();
    storage.clear();
  });

  it('attaches a Bearer token to API requests', () => {
    storage.store({ accessToken: 'tok', refreshToken: 'r', expiresIn: 1800 });
    http.get(`${environment.auditApiUrl}/api/v1/audit-logs`).subscribe();
    const req = httpMock.expectOne(`${environment.auditApiUrl}/api/v1/audit-logs`);
    expect(req.request.headers.get('Authorization')).toBe('Bearer tok');
    req.flush([]);
  });

  it('does not attach a token to the login exchange', () => {
    storage.store({ accessToken: 'tok', refreshToken: 'r', expiresIn: 1800 });
    http.post(`${environment.authApiUrl}/auth/login`, {}).subscribe();
    const req = httpMock.expectOne(`${environment.authApiUrl}/auth/login`);
    expect(req.request.headers.has('Authorization')).toBeFalse();
    req.flush({ accessToken: 'a', refreshToken: 'r', expiresIn: 1800 });
  });

  it('refreshes once on 401 then retries the original request', () => {
    storage.store({ accessToken: 'stale', refreshToken: 'r', expiresIn: 1800 });
    http.get(`${environment.auditApiUrl}/api/v1/audit-logs`).subscribe();

    // First attempt → 401
    httpMock
      .expectOne(`${environment.auditApiUrl}/api/v1/audit-logs`)
      .flush(null, { status: 401, statusText: 'Unauthorized' });

    // Interceptor calls refresh
    const refresh = httpMock.expectOne(`${environment.authApiUrl}/auth/refresh`);
    refresh.flush({ accessToken: 'fresh', refreshToken: 'r2', expiresIn: 1800 });

    // Retry carries the new token
    const retry = httpMock.expectOne(`${environment.auditApiUrl}/api/v1/audit-logs`);
    expect(retry.request.headers.get('Authorization')).toBe('Bearer fresh');
    retry.flush([]);
  });
});
