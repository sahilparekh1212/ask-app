// Off-peak production smoke test for the Audit read API (search + stats), run against the live
// deployment through the public origin (Caddy -> ui nginx -> Audit). Read-only: it does a single
// demo login for a bearer token, then only GETs — it never writes to prod, and it deliberately
// excludes the paid AI endpoints (chat/flashcards/RAG).
//
// Unlike the CI load test (search-stats.js), prod runs the newest-wins rate limiter ON and
// buckets per user; the demo login always mints the same user id, so concurrency is kept modest
// to measure the VM's real read latency rather than the limiter's shedding. 429s (the limiter
// doing its job) and any 5xx are counted separately; p95 latency is measured over 200s only.
//
//   docker run --rm -i grafana/k6 run - < Backend/load-test/prod-smoke.js
//   ... or: k6 run -e BASE_URL=https://ask-app.sahilparekh1212.com Backend/load-test/prod-smoke.js
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const BASE = __ENV.BASE_URL || 'https://ask-app.sahilparekh1212.com';
const AUTH = `${BASE}/auth-api`;
const AUDIT = `${BASE}/audit-api`;

const rateLimited = new Counter('rate_limited_429');
const serverErrors = new Counter('server_5xx');
const readLatency = new Trend('read_latency_ms', true); // p95 over successful 200 reads only

const ENTITY = ['User', 'Order', 'Invoice', 'Payment'];

export const options = {
  scenarios: {
    smoke: {
      executor: 'ramping-vus',
      startVUs: 1,
      stages: [
        { duration: '10s', target: 5 }, // ramp
        { duration: '30s', target: 5 }, // sustained modest load
        { duration: '5s', target: 0 }, // ramp down
      ],
    },
  },
  thresholds: {
    server_5xx: ['count==0'], // the shared VM must never 5xx under this load
    read_latency_ms: ['p(95)<1500'], // generous ceiling for a $50 VM over public TLS
  },
};

export function setup() {
  const res = http.post(
    `${AUTH}/auth/login`,
    JSON.stringify({ username: 'demo', password: 'demo', role: 'ROLE_ADMIN' }),
    { headers: { 'Content-Type': 'application/json' } },
  );
  check(res, { 'login 200': (r) => r.status === 200 });
  return { token: res.json('accessToken') };
}

export default function (data) {
  const headers = { Authorization: `Bearer ${data.token}` };
  const entity = ENTITY[Math.floor(Math.random() * ENTITY.length)];

  classify(
    http.get(
      `${AUDIT}/api/v1/audit-logs/search?entityType=${entity}&page=0&size=20&sort=createdAt,desc`,
      { headers },
    ),
  );
  classify(http.get(`${AUDIT}/api/v1/audit-logs/stats?entityType=${entity}`, { headers }));

  sleep(0.5);
}

function classify(r) {
  if (r.status === 429) rateLimited.add(1);
  else if (r.status >= 500) serverErrors.add(1);
  else if (r.status === 200) readLatency.add(r.timings.duration);
  check(r, { 'no 5xx': (x) => x.status < 500 });
}
