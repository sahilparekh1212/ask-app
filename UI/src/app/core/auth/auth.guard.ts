import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { TokenStorageService } from './token-storage.service';

/**
 * Blocks protected routes when there's no access token, redirecting to /login and remembering the
 * attempted URL so the login page can send the user back after authenticating.
 */
export const authGuard: CanActivateFn = (_route, state) => {
  const storage = inject(TokenStorageService);
  const router = inject(Router);

  if (storage.accessToken) {
    return true;
  }
  return router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};
