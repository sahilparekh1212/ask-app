import {
  ApplicationConfig,
  ErrorHandler,
  inject,
  provideAppInitializer,
  provideZoneChangeDetection,
} from '@angular/core';
import { TitleStrategy, provideRouter, withInMemoryScrolling } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import * as Sentry from '@sentry/angular';
import { catchError, firstValueFrom, of } from 'rxjs';

import { routes } from './app.routes';
import { authInterceptor } from './core/auth/auth.interceptor';
import { AnalyticsService } from './core/analytics/analytics.service';
import { FeatureFlagService } from './core/feature-flags/feature-flag.service';
import { TokenStorageService } from './core/auth/token-storage.service';
import { AppTitleStrategy } from './core/title.strategy';
import { environment } from '../environments/environment';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    // anchorScrolling makes the sidebar's fragment links (e.g. /about#features) scroll to the
    // matching element id; scrollPositionRestoration resets to top on a normal navigation.
    provideRouter(
      routes,
      withInMemoryScrolling({ anchorScrolling: 'enabled', scrollPositionRestoration: 'enabled' }),
    ),
    // Browser-tab titles as "Ask App - <page>", derived from each route's `title`.
    { provide: TitleStrategy, useClass: AppTitleStrategy },
    // authInterceptor attaches the Bearer token and does silent refresh-on-401.
    provideHttpClient(withInterceptors([authInterceptor])),
    // GA4 page-view tracking; a no-op unless the environment carries a Measurement ID.
    provideAppInitializer(() => inject(AnalyticsService).init()),
    // Load UI feature flags before first paint when a session already exists (reload case), so the
    // nav/features don't flicker. Fresh visitors have no token — skip the guaranteed-401 and let
    // FeatureFlagService's auth effect load them right after login. Fail-open on any error.
    provideAppInitializer(() => {
      const flags = inject(FeatureFlagService);
      return inject(TokenStorageService).accessToken
        ? firstValueFrom(flags.load().pipe(catchError(() => of(null))))
        : undefined;
    }),
    // Sentry's ErrorHandler forwards uncaught errors; without a DSN the default handler
    // stays, so dev keeps plain console errors.
    ...(environment.sentryDsn
      ? [{ provide: ErrorHandler, useValue: Sentry.createErrorHandler() }]
      : []),
  ],
};
