import { bootstrapApplication } from '@angular/platform-browser';
import * as Sentry from '@sentry/angular';

import { appConfig } from './app/app.config';
import { AppComponent } from './app/app.component';
import { environment } from './environments/environment';

// Sentry initializes before Angular bootstraps so even bootstrap failures are captured.
// No DSN (dev, or a fork without a Sentry project) → no init, no network, default error
// handling — the same graceful "not configured" posture as the backend's provider keys.
if (environment.sentryDsn) {
  Sentry.init({
    dsn: environment.sentryDsn,
    environment: 'production',
    // Error monitoring only — no performance/replay integrations, keeping the bundle and
    // the data collection minimal. Revisit if browser-side tracing ever earns its weight.
    sendDefaultPii: false,
  });
}

bootstrapApplication(AppComponent, appConfig).catch((err) => console.error(err));
