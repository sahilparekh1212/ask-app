import { inject } from '@angular/core';
import { CanActivateFn, Router, UrlTree } from '@angular/router';
import { FeatureFlagService } from './feature-flag.service';

/**
 * The flag-gated major routes, in fallback priority order: if the requested route's feature is off,
 * send the user to the first enabled one. `/profile` is the final, ungated fallback (so there's no
 * redirect loop when every major feature is disabled).
 */
const MAJOR_ROUTES: { path: string; flag: string }[] = [
  { path: '/chat', flag: 'chat' },
  { path: '/observability', flag: 'observability' },
];

/**
 * Blocks a route when its feature flag is off, redirecting to the first still-enabled major route
 * (final fallback `/profile`). Composed after `authGuard`, so this only runs for signed-in users.
 * With all flags on (the default) it always allows — behavior is unchanged.
 */
export function featureFlagGuard(flagKey: string): CanActivateFn {
  return (): boolean | UrlTree => {
    const flags = inject(FeatureFlagService);
    const router = inject(Router);

    if (flags.isEnabled(flagKey)) {
      return true;
    }
    const fallback = MAJOR_ROUTES.find((r) => flags.isEnabled(r.flag))?.path ?? '/profile';
    return router.createUrlTree([fallback]);
  };
}
