import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { AuthService } from './auth.service';
import { TokenStorageService } from './token-storage.service';
import { environment } from '../../../environments/environment';

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;
  let storage: TokenStorageService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
    storage = TestBed.inject(TokenStorageService);
    storage.clear();
  });

  afterEach(() => {
    httpMock.verify();
    storage.clear();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('stores tokens on demo login', () => {
    service.demoLogin({ username: 'demo', password: 'demo' }).subscribe();
    const req = httpMock.expectOne(`${environment.authApiUrl}/auth/login`);
    expect(req.request.method).toBe('POST');
    req.flush({ accessToken: 'a', refreshToken: 'r', expiresIn: 1800 });
    expect(storage.accessToken).toBe('a');
    expect(storage.refreshToken).toBe('r');
  });

  it('consumes tokens from an OAuth fragment and reports success', () => {
    const ok = service.consumeOAuthFragment('access_token=aa&refresh_token=rr&expires_in=1800');
    expect(ok).toBeTrue();
    expect(storage.accessToken).toBe('aa');
  });

  it('returns false for a fragment without tokens', () => {
    expect(service.consumeOAuthFragment('foo=bar')).toBeFalse();
    expect(service.consumeOAuthFragment(null)).toBeFalse();
  });

  it('flips isAuthenticated reactively on login without ever loading the profile', () => {
    // The regression this guards: a returnUrl login never visits /profile, so nothing set
    // the _profile signal — and localStorage isn't reactive, so the header's computed
    // stayed false and kept showing "Login" until a hard reload.
    expect(service.isAuthenticated()).toBeFalse();

    service.demoLogin({ username: 'demo', password: 'demo' }).subscribe();
    httpMock
      .expectOne(`${environment.authApiUrl}/auth/login`)
      .flush({ accessToken: 'a', refreshToken: 'r', expiresIn: 1800 });

    expect(service.isAuthenticated()).toBeTrue();

    service.clearSession();
    expect(service.isAuthenticated()).toBeFalse();
  });

  it('flips isAuthenticated reactively on an OAuth fragment handoff', () => {
    expect(service.isAuthenticated()).toBeFalse();
    service.consumeOAuthFragment('access_token=aa&refresh_token=rr&expires_in=1800');
    expect(service.isAuthenticated()).toBeTrue();
  });
});
