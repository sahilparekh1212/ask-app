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

import { routes } from './app.routes';
import { authInterceptor } from './core/auth/auth.interceptor';
import { AnalyticsService } from './core/analytics/analytics.service';
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
    // Sentry's ErrorHandler forwards uncaught errors; without a DSN the default handler
    // stays, so dev keeps plain console errors.
    ...(environment.sentryDsn
      ? [{ provide: ErrorHandler, useValue: Sentry.createErrorHandler() }]
      : []),
  ],
};
