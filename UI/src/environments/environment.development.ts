/**
 * Local dev: call the same relative /auth-api and /audit-api prefixes production uses. The
 * dev-server proxy (proxy.config.js) forwards them to Auth (:8085) and Audit (:8083) and strips
 * the prefix, mirroring nginx — so the browser only ever talks to http://localhost:4200 and CORS
 * never enters the picture, exactly like prod. Both services must be running (compose stack or
 * `gradlew bootRun`).
 */
export const environment = {
  production: false,
  authApiUrl: '/auth-api',
  auditApiUrl: '/audit-api',
  // The dev server has no /grafana proxy — link straight to the compose stack's Grafana.
  grafanaUrl: 'http://localhost:3000/grafana',
  // Always empty in dev: local clicks must never pollute the real GA property, and local
  // errors must never page anyone via Sentry.
  gaMeasurementId: '',
  sentryDsn: '',
};
