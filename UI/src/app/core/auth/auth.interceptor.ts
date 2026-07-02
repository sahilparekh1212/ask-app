import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthService } from './auth.service';
import { TokenStorageService } from './token-storage.service';

/** Endpoints that must NOT carry a Bearer token (they mint/rotate it themselves). */
function isAuthExchange(url: string): boolean {
  return url.includes('/auth/login') || url.includes('/auth/refresh');
}

/** Only attach the token to our own APIs, never to third-party origins. */
function isApiRequest(url: string): boolean {
  return url.startsWith(environment.authApiUrl) || url.startsWith(environment.auditApiUrl);
}

function withBearer<T>(req: HttpRequest<T>, token: string): HttpRequest<T> {
  return req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
}

/**
 * Attaches `Authorization: Bearer <access>` to API calls, and on a 401 tries a single silent
 * refresh (POST /auth/refresh) before retrying the original request once. If refresh fails, the
 * session is cleared and the user is sent to /login.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const storage = inject(TokenStorageService);
  const auth = inject(AuthService);
  const router = inject(Router);

  const token = storage.accessToken;
  const shouldAttach = isApiRequest(req.url) && !isAuthExchange(req.url) && !!token;
  const outgoing = shouldAttach ? withBearer(req, token) : req;

  return next(outgoing).pipe(
    catchError((error: HttpErrorResponse) => {
      const canRetry =
        error.status === 401 && shouldAttach && !isAuthExchange(req.url) && !!storage.refreshToken;
      if (!canRetry) {
        return throwError(() => error);
      }
      return auth.refresh().pipe(
        switchMap((tokens) => next(withBearer(req, tokens.accessToken))),
        catchError((refreshError) => {
          auth.clearSession();
          void router.navigate(['/login']);
          return throwError(() => refreshError);
        }),
      );
    }),
  );
};
