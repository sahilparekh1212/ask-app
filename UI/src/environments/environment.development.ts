/**
 * Local dev: call the two services directly on their own ports. Both enable CORS for the Angular
 * dev server origin (http://localhost:4200) via CORS_ALLOWED_ORIGINS — see the backend README.
 */
export const environment = {
  production: false,
  authApiUrl: 'http://localhost:8085',
  auditApiUrl: 'http://localhost:8083',
  // The dev server has no /grafana proxy — link straight to the compose stack's Grafana.
  grafanaUrl: 'http://localhost:3000/grafana',
  // Always empty in dev: local clicks must never pollute the real GA property, and local
  // errors must never page anyone via Sentry.
  gaMeasurementId: '',
  sentryDsn: '',
};
