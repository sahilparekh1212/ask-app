import { ApplicationConfig, provideZoneChangeDetection } from '@angular/core';
import { provideRouter, withInMemoryScrolling } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';

import { routes } from './app.routes';
import { authInterceptor } from './core/auth/auth.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    // anchorScrolling makes the sidebar's fragment links (e.g. /about#features) scroll to the
    // matching element id; scrollPositionRestoration resets to top on a normal navigation.
    provideRouter(
      routes,
      withInMemoryScrolling({ anchorScrolling: 'enabled', scrollPositionRestoration: 'enabled' }),
    ),
    // authInterceptor attaches the Bearer token and does silent refresh-on-401.
    provideHttpClient(withInterceptors([authInterceptor])),
  ],
};
