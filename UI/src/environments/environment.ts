/**
 * Production defaults. In a deployed setup these are same-origin paths proxied (by nginx / the
 * gateway) to the Auth and Audit services, so the SPA never hardcodes a backend host.
 */
export const environment = {
  production: true,
  authApiUrl: '/auth-api',
  auditApiUrl: '/audit-api',
  // Same-origin like the APIs: Caddy routes it in prod, the ui nginx proxies it in the local
  // compose stack — so the system-view links work wherever this build is served.
  grafanaUrl: '/grafana',
  // GA4 Measurement ID (public by design — it ships in every page's HTML on any GA site).
  // Empty string disables analytics entirely: no script load, no network calls.
  gaMeasurementId: 'G-Q84781MSCE',
  // Sentry DSN (publishable, not a secret — it can only ingest events, not read them).
  // Empty string disables Sentry entirely: no init, default Angular ErrorHandler.
  sentryDsn: 'https://2df193ee7a26145965f701b8916b812a@o4511723457282048.ingest.us.sentry.io/4511723478253568',
};
