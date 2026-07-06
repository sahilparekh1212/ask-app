import { defineConfig } from '@playwright/test';

/**
 * Runs against an already-started compose stack (Backend/docker-compose.yml), not a dev server
 * Playwright boots itself — the point of this suite is the real system: the nginx-served
 * production UI bundle proxying to Auth/Audit, with Kafka and Postgres behind them.
 * Start it with `docker compose up --build` in Backend/ first (or let CI do it — see
 * .github/workflows/e2e.yml), then `npm test` here.
 */
export default defineConfig({
  testDir: './tests',
  timeout: 60_000,
  expect: { timeout: 10_000 },
  // The tests mutate shared server state (row counts, generated demo logs), so they run
  // one at a time; assertions on totals are written as >= deltas to stay robust anyway.
  fullyParallel: false,
  workers: 1,
  // One retry in CI: the stack is genuinely asynchronous (Kafka consumption, stats reloads).
  // The trace of the first failing attempt is still captured and uploaded.
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:4200',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { browserName: 'chromium' } }],
});
