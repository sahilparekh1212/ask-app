/**
 * Angular dev-server proxy — makes `ng serve` mirror the production same-origin model.
 *
 * Both builds now call the relative prefixes `/auth-api` and `/audit-api` (see
 * `src/environments/`). In production nginx reverse-proxies those prefixes into the compose
 * network and strips the prefix before the upstream sees it (`proxy_pass http://auth:8085/` — the
 * trailing slash is what strips `/auth-api`; see `nginx.conf`). This file does the same for local
 * `npm start`: it forwards each prefix to the service on its own port and rewrites the prefix
 * away, so the browser only ever talks to `http://localhost:4200` and CORS never enters the
 * picture — exactly like prod. Dev therefore no longer relies on the backends' CORS allowance.
 *
 * Both services must be running (the compose stack, or `gradlew bootRun` per the backend README).
 * CommonJS on purpose: package.json has no `"type": "module"`, so the Angular CLI require()s this.
 */
module.exports = {
  '/auth-api': {
    target: 'http://localhost:8085',
    changeOrigin: true,
    pathRewrite: { '^/auth-api': '' },
  },
  '/audit-api': {
    target: 'http://localhost:8083',
    changeOrigin: true,
    pathRewrite: { '^/audit-api': '' },
  },
};
