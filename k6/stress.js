// Staged ramp to simulate low/med/high/extreme load touching all endpoints.
import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  thresholds: {
    http_req_failed: ['rate<0.01'],     // <1% errors
    http_req_duration: ['p(95)<2000'],  // 95% < 2s (example)
  },
  stages: [
    { duration: '1m', target: 10 },   // low
    { duration: '2m', target: 30 },   // medium
    { duration: '3m', target: 60 },   // high
    { duration: '2m', target: 100 },  // extreme
    { duration: '2m', target: 0 },    // ramp down
  ],
};

const BASE = __ENV.APP_URL || 'http://app:8080';
const uris = [
  '/api/cpu-intensive?iterations=500000',
  '/api/memory-intensive?sizeMb=100',
  '/api/database-intensive?ops=500',
  '/api/combined-stress?durationSec=5',
];

export default function () {
  const url = BASE + uris[Math.floor(Math.random()*uris.length)];
  const res = http.get(url, { timeout: '30s' });
  check(res, { '200': (r) => r.status === 200 });
  sleep(0.1);
}
