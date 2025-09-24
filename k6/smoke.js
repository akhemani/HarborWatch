// Simple sanity: a few quick hits to ensure everything is alive.
import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  vus: 3,
  duration: '30s',
};

const BASE = __ENV.APP_URL || 'http://app:8080';

export default function () {
  const endpoints = [
    `${BASE}/actuator/health`,
    `${BASE}/api/cpu-intensive?iterations=200000`,
    `${BASE}/api/memory-intensive?sizeMb=50`,
    `${BASE}/api/database-intensive?ops=200`,
  ];
  const res = http.get(endpoints[Math.floor(Math.random()*endpoints.length)]);
  check(res, { 'status is 200': (r) => r.status === 200 });
  sleep(0.2);
}
