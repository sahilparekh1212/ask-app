import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  RouterStateSnapshot,
  UrlTree,
  provideRouter,
} from '@angular/router';

import { featureFlagGuard } from './feature-flag.guard';
import { FeatureFlagService } from './feature-flag.service';

describe('featureFlagGuard', () => {
  const enabled = new Set<string>();
  const flagsStub = { isEnabled: (key: string) => enabled.has(key) };

  const run = (flag: string) =>
    TestBed.runInInjectionContext(() =>
      featureFlagGuard(flag)({} as ActivatedRouteSnapshot, { url: '/x' } as RouterStateSnapshot),
    );

  beforeEach(() => {
    enabled.clear();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: FeatureFlagService, useValue: flagsStub }],
    });
  });

  it('allows navigation when the feature is enabled', () => {
    enabled.add('chat');
    expect(run('chat')).toBeTrue();
  });

  it('redirects a disabled feature to the first still-enabled major route', () => {
    enabled.add('observability'); // chat off, observability on
    const result = run('chat');
    expect(result instanceof UrlTree).toBeTrue();
    expect((result as UrlTree).toString()).toContain('/observability');
  });

  it('falls back to /profile when every major feature is off', () => {
    const result = run('chat'); // nothing enabled
    expect(result instanceof UrlTree).toBeTrue();
    expect((result as UrlTree).toString()).toContain('/profile');
  });
});
