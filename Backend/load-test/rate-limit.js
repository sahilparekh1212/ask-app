// k6 behaviour test for the rate limiter ("one active request per user+endpoint, newest
// wins"). Run against an Audit instance started with the LOADTEST profile and the rate
// limiter ENABLED (the default). Many concurrent VUs POST to the same endpoint as the same
// (anonymous) identity, so in-flight creates are continuously superseded and discarded.
//
// The hard gate is: the limiter must shed load as HTTP 429, never as a 5xx. The observed
// 429 share is reported as a metric (informational, not gated, to avoid timing flakiness).
//
//   k6 run Backend/load-test/rate-limit.js
import http from 'k6/http';
import { check } from 'k6';
import { Rate } from 'k6/metrics';

const BASE = __ENV.BASE_URL || 'http://localhost:8083';
const rateLimited = new Rate('rate_limited_429');
const serverErrors = new Rate('server_errors_5xx');

export const options = {
  scenarios: {
    burst: {
      executor: 'constant-vus',
      vus: 30,            // heavy same-key contention
      duration: '20s',
    },
  },
  thresholds: {
    // Shedding must be graceful: a superseded request becomes 429, effectively never a
    // server error. Allow <1% to absorb the rare interrupt-during-JDBC race on a CI runner.
    server_errors_5xx: ['rate<0.01'],
  },
};

export default function () {
  const body = JSON.stringify({ entityType: 'User', action: 'CREATE', details: 'rl' });
  const res = http.post(`${BASE}/api/audit-logs`, body, {
    headers: { 'Content-Type': 'application/json' },
  });

  rateLimited.add(res.status === 429);
  serverErrors.add(res.status >= 500);
  check(res, {
    'no server error': (r) => r.status < 500,
    'created or rate-limited': (r) => r.status === 201 || r.status === 429,
  });
}
