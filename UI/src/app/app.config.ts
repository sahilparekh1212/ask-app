import {
  ApplicationConfig,
  ErrorHandler,
  inject,
  provideAppInitializer,
  provideZoneChangeDetection,
} from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import * as Sentry from '@sentry/angular';

import { routes } from './app.routes';
import { authInterceptor } from './core/auth/auth.interceptor';
import { AnalyticsService } from './core/analytics/analytics.service';
import { environment } from '../environments/environment';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
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
