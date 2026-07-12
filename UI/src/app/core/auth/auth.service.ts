import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { DemoLoginRequest, TokenResponse, UserProfile } from './auth.models';
import { TokenStorageService } from './token-storage.service';

/**
 * Owns the auth lifecycle: demo/Google login, the OAuth fragment handoff, token refresh, logout,
 * and the current-user profile. Exposes an `isAuthenticated` signal the shell/guards read.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly storage = inject(TokenStorageService);
  private readonly base = environment.authApiUrl;

  private readonly _profile = signal<UserProfile | null>(null);
  readonly profile = this._profile.asReadonly();
  // localStorage isn't reactive, so every token store/clear bumps this signal — without it,
  // a login that never lands on /profile (e.g. a returnUrl bounce back to the guarded page)
  // left isAuthenticated stale and the header kept showing "Login" instead of the avatar.
  private readonly _tokenVersion = signal(0);
  readonly isAuthenticated = computed(() => {
    this._tokenVersion();
    return !!this._profile() || !!this.storage.accessToken;
  });

  /** Demo login — issues the same JWTs as Google sign-in, no IdP setup required. */
  demoLogin(request: DemoLoginRequest): Observable<TokenResponse> {
    return this.http
      .post<TokenResponse>(`${this.base}/auth/login`, request)
      .pipe(tap((tokens) => this.storeTokens(tokens)));
  }

  /** Kick off the Google OAuth2 flow on the Auth server; it redirects back to /login/callback. */
  loginWithGoogle(): void {
    window.location.href = `${this.base}/oauth2/authorization/google`;
  }

  /**
   * Consume the tokens the OAuth success handler puts in the redirect URL *fragment*
   * (#access_token=...&refresh_token=...&expires_in=...). Returns true if a token was found.
   */
  consumeOAuthFragment(fragment: string | null): boolean {
    if (!fragment) {
      return false;
    }
    const params = new URLSearchParams(fragment);
    const accessToken = params.get('access_token');
    const refreshToken = params.get('refresh_token');
    if (!accessToken || !refreshToken) {
      return false;
    }
    this.storeTokens({
      accessToken,
      refreshToken,
      expiresIn: Number(params.get('expires_in') ?? 0),
    });
    return true;
  }

  /** Exchange the refresh token for a fresh pair (used by the interceptor on 401). */
  refresh(): Observable<TokenResponse> {
    return this.http
      .post<TokenResponse>(`${this.base}/auth/refresh`, { refreshToken: this.storage.refreshToken })
      .pipe(tap((tokens) => this.storeTokens(tokens)));
  }

  /** Load the current user's profile from the token (GET /auth/me). */
  loadProfile(): Observable<UserProfile> {
    return this.http
      .get<UserProfile>(`${this.base}/auth/me`)
      .pipe(tap((profile) => this._profile.set(profile)));
  }

  logout(): Observable<void> {
    const refreshToken = this.storage.refreshToken;
    this.clearSession();
    return this.http.post<void>(`${this.base}/auth/logout`, { refreshToken });
  }

  /** Local-only teardown (no server call) — used by the interceptor when refresh fails. */
  clearSession(): void {
    this.storage.clear();
    this._profile.set(null);
    this._tokenVersion.update((v) => v + 1);
  }

  private storeTokens(tokens: TokenResponse): void {
    this.storage.store(tokens);
    this._tokenVersion.update((v) => v + 1);
  }
}
