/**
 * Production defaults. In a deployed setup these are same-origin paths proxied (by nginx / the
 * gateway) to the Auth and Audit services, so the SPA never hardcodes a backend host.
 */
export const environment = {
  production: true,
  authApiUrl: '/auth-api',
  auditApiUrl: '/audit-api',
};
