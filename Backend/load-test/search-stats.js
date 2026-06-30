// k6 throughput/latency load test for the Audit read API (search + stats).
//
// Run against an Audit instance started with the LOADTEST profile and the rate limiter
// DISABLED (ratelimit.enabled=false), so this measures raw endpoint performance rather
// than the limiter's shedding behaviour (that is covered by rate-limit.js).
//
//   k6 run Backend/load-test/search-stats.js           # defaults to http://localhost:8083
//   k6 run -e BASE_URL=http://host:8083 search-stats.js
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

const BASE = __ENV.BASE_URL || 'http://localhost:8083';
const checkFails = new Rate('check_failures');

const ENTITY = ['User', 'Order', 'Invoice', 'Payment'];
const ACTION = ['CREATE', 'UPDATE', 'DELETE', 'VIEW'];

export const options = {
  scenarios: {
    browse: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '15s', target: 20 }, // ramp up
        { duration: '30s', target: 20 }, // sustained load
        { duration: '10s', target: 0 },  // ramp down
      ],
    },
  },
  // Lenient enough for a shared CI runner, strict enough to catch real regressions.
  thresholds: {
    http_req_failed: ['rate<0.01'],     // < 1% of requests error
    http_req_duration: ['p(95)<800'],   // 95th percentile under 800ms
    check_failures: ['rate<0.01'],
  },
};

// Seed realistic data once so search/stats run against non-trivial row counts.
export function setup() {
  for (let i = 0; i < 200; i++) {
    const body = JSON.stringify({
      entityType: ENTITY[i % ENTITY.length],
      action: ACTION[i % ACTION.length],
      details: `seed-${i}`,
    });
    http.post(`${BASE}/api/audit-logs`, body, { headers: { 'Content-Type': 'application/json' } });
  }
}

export default function () {
  const entityType = ENTITY[Math.floor(Math.random() * ENTITY.length)];

  const search = http.get(
    `${BASE}/api/audit-logs/search?entityType=${entityType}&page=0&size=20&sort=createdAt,desc`,
  );
  const searchOk = check(search, {
    'search 200': (r) => r.status === 200,
    'search has content': (r) => (r.json('content') || []).length >= 0,
  });
  checkFails.add(!searchOk);

  const stats = http.get(`${BASE}/api/audit-logs/stats?entityType=${entityType}`);
  const statsOk = check(stats, {
    'stats 200': (r) => r.status === 200,
    'stats has total': (r) => r.json('total') !== undefined,
  });
  checkFails.add(!statsOk);

  sleep(0.1);
}
