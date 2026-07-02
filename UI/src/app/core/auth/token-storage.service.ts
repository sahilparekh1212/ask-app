import { Injectable } from '@angular/core';
import { TokenResponse } from './auth.models';

const ACCESS_KEY = 'ais.accessToken';
const REFRESH_KEY = 'ais.refreshToken';

/**
 * Persists the JWT pair in localStorage so a page reload keeps the session. Access tokens are
 * short-lived (30 min) and re-mintable via the refresh token, so this is an acceptable store for a
 * demo/portfolio SPA; a hardened deployment would prefer httpOnly cookies (see the backend README's
 * OAuth-handoff note).
 */
@Injectable({ providedIn: 'root' })
export class TokenStorageService {
  get accessToken(): string | null {
    return localStorage.getItem(ACCESS_KEY);
  }

  get refreshToken(): string | null {
    return localStorage.getItem(REFRESH_KEY);
  }

  store(tokens: TokenResponse): void {
    localStorage.setItem(ACCESS_KEY, tokens.accessToken);
    localStorage.setItem(REFRESH_KEY, tokens.refreshToken);
  }

  clear(): void {
    localStorage.removeItem(ACCESS_KEY);
    localStorage.removeItem(REFRESH_KEY);
  }
}
