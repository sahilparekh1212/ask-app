import { Injectable, computed, effect, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthService } from '../auth/auth.service';
import { FeatureFlag } from './feature-flag.models';

/**
 * Holds the deployment's UI feature flags (see ADR-0015). The SPA reads them once to decide which
 * major features to show (chat, voice, hints, observability). Flags are DB-backed and read-only from
 * the app — they're flipped in the database, not here.
 *
 * <p><b>Fail-open:</b> until the flags have loaded — the login page, a transient error, or the brief
 * window right after a fresh login — every flag reads as enabled, so the app is never bricked. These
 * flags are a curation/rollout tool, not a security control (authorization stays server-side).
 *
 * <p><b>Load timing:</b> the endpoint is authenticated. On a reload-with-session, `app.config`'s
 * initializer loads flags before first paint. On a fresh login there's no token at bootstrap, so the
 * `effect` below loads them the moment {@link AuthService.isAuthenticated} flips true. Flags are
 * deployment-scoped, so they are not reset on logout.
 */
@Injectable({ providedIn: 'root' })
export class FeatureFlagService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly url = `${environment.auditApiUrl}/api/v1/meta/flags`;

  // null = not yet loaded → fail-open (every flag reads as enabled).
  private readonly _flags = signal<Record<string, boolean> | null>(null);
  /** True once a successful load has populated the flags (mainly for tests/diagnostics). */
  readonly loaded = computed(() => this._flags() !== null);

  constructor() {
    // Fresh-login case: the initializer ran token-less and skipped the guaranteed-401 fetch, so
    // load once we're authenticated and still have no flags. Mirrors AppComponent's profile effect.
    // The signal write happens later in the HTTP callback, not synchronously here, so there's no loop.
    effect(() => {
      if (this.auth.isAuthenticated() && this._flags() === null) {
        this.load().subscribe({ error: () => undefined });
      }
    });
  }

  /** Fetch the flag list and fold it into a key→enabled map. Errors leave flags null (fail-open). */
  load(): Observable<FeatureFlag[]> {
    return this.http
      .get<FeatureFlag[]>(this.url)
      .pipe(
        tap((list) => this._flags.set(Object.fromEntries(list.map((f) => [f.key, f.enabled])))),
      );
  }

  /**
   * Whether a feature is enabled. Reading the underlying signal makes callers (computeds/templates)
   * recompute when flags arrive. Unknown key or not-yet-loaded → true (fail-open).
   */
  isEnabled(key: string): boolean {
    const flags = this._flags();
    return flags ? (flags[key] ?? true) : true;
  }
}
