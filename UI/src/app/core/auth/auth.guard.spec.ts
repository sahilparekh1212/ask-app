import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  CanActivateFn,
  RouterStateSnapshot,
  UrlTree,
  provideRouter,
} from '@angular/router';

import { authGuard } from './auth.guard';
import { TokenStorageService } from './token-storage.service';

describe('authGuard', () => {
  const run: CanActivateFn = (...params) =>
    TestBed.runInInjectionContext(() => authGuard(...params));

  let storage: TokenStorageService;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    storage = TestBed.inject(TokenStorageService);
    storage.clear();
  });

  const state = { url: '/profile' } as RouterStateSnapshot;
  const route = {} as ActivatedRouteSnapshot;

  it('allows navigation when an access token exists', () => {
    storage.store({ accessToken: 'a', refreshToken: 'r', expiresIn: 1800 });
    expect(run(route, state)).toBeTrue();
  });

  it('redirects to /login when there is no token', () => {
    const result = run(route, state);
    expect(result instanceof UrlTree).toBeTrue();
    expect((result as UrlTree).toString()).toContain('/login');
  });
});
