# HarborWatch — Step 5: Central Scrape + Alerting (Core App + Postgres + Health + LoadServer + DebugAPIs + Background Scheduler + Prometheus + Alertmanager + Blackbox + SES + Load Testing + Visualization + Hardening)

Step 5, focusing on load testing, visualization, and production hardening. The goal is to provide reliable dashboards, repeatable stress tests to validate end-to-end behavior, and production-ready configurations.

## Stack (Final)

- **App**: Spring Boot (Actuator + Micrometer Prometheus)
- **Metrics Sources**:
  - `/actuator/prometheus` (app metrics)
  - cAdvisor (container metrics)
  - node_exporter (host metrics)
  - blackbox-exporter (active probes)
- **Collection & Alerting**: Prometheus + Alertmanager → SES (from Step 4)
- **Visualization**: Grafana (auto-provisioned Prometheus datasource + SRE Overview + imported dashboards)
- **Load Testing**: k6 scripts (`smoke.js`, `stress.js`) + optional `cli_live_dashboard.sh` menu script
- **Optional**: k6 runner container for tests inside the compose network

## What's New in Step 5

- **k6 Load Scripts**: Smoke and staged stress scenarios
- **Grafana**:
  - Auto-provisioned Prometheus datasource
  - SRE Overview dashboard (p50/p95 latency, error rate, container CPU/Mem, uptime, Alertmanager link)
  - Guidance for importing community dashboards (Node Exporter, cAdvisor, Blackbox, Micrometer/JVM)
- **App Hardening**:
  - Readiness/liveness probes
  - Resource limits
  - Basic server/pool timeouts
- **Optional k6 Runner**: Run tests within the compose network
- **Time Zone**: IST enforced across app logs and DB (`timestamptz`)

## Repository Layout

```plaintext
harborwatch/
  ├─ docker-compose.yml
  ├─ cli_live_dashboard.sh                       # Menu: quick stress + metrics snapshots
  ├─ k6/
  │  ├─ smoke.js                         # Smoke test script
  │  └─ stress.js                        # Staged stress test script
  ├─ monitoring/
  │  ├─ prometheus.yml                   # Central scrape config (Step 4)
  │  ├─ alerts.yml                       # Alert rules (Step 4)
  │  ├─ blackbox.yml                     # Probe module (Step 4)
  │  ├─ grafana/
  │  │  ├─ provisioning/
  │  │  │  ├─ datasources/datasource.yml # Prometheus datasource
  │  │  │  └─ dashboards/dashboards.yml  # Points Grafana to dashboards
  │  │  └─ dashboards/
  │  │     └─ sre-overview.json          # SRE cockpit dashboard
  │  ├─ Alertmanager.Dockerfile          # Custom non-root image (Step 4)
  │  ├─ alertmanager-run.sh             # Renders template with env (Step 4)
  │  └─ alertmanager.yml.tmpl           # Alertmanager config template (Step 4)
  └─ src/                               # Spring Boot app (Steps 1–4)
```

## Running the Stack

1. **Build and run the App (if changed)**:
   ```bash
   docker compose up -d --build
   ```

2. **Access Services**:
   - App: [http://localhost:8080](http://localhost:8080)
     - Endpoints: `/actuator/health`, `/actuator/health/readiness`, `/actuator/prometheus`
   - Prometheus: [http://localhost:9090](http://localhost:9090)
   - Alertmanager: [http://localhost:9093](http://localhost:9093)
   - cAdvisor: [http://localhost:8085](http://localhost:8085)
   - node_exporter: [http://localhost:9100/metrics](http://localhost:9100/metrics)
   - Grafana: [http://localhost:3000](http://localhost:3000) (default: `admin/admin`)

3. **Troubleshooting Dashboards**:
   - If a dashboard shows "No data":
     - Check Prometheus targets (Prometheus → Status → Targets)
     - Ensure time range (e.g., Last 15m) and refresh (5–10s) are reasonable

## Grafana Dashboards

### 1. SRE Overview (Included)
- **Location**: Dashboards → HarborWatch — SRE Overview
- **Panels**:
  - App latency (p50/p95): `histogram_quantile` on `http_server_requests_seconds_bucket`
  - App error rate (%): 5xx / total requests
  - Container CPU (cores): `rate(container_cpu_usage_seconds_total{name="hw-app"}[1m])`
  - Container Memory (%): `100 * working_set / limit` (cAdvisor)
  - Uptime (binary): `probe_success` (blackbox)
  - Alertmanager link: Quick jump to incidents

### 2. Import Community Dashboards (Recommended)
- **Location**: Grafana → Dashboards → Import
- **Search for**:
  - **Node Exporter**: Host metrics (CPU modes, memory, disk IO, network)
  - **cAdvisor/Docker**: Per-container CPU/Mem/IO (filter to `hw-app`, `hw-db`, etc.)
  - **Blackbox Exporter**: Probe success and latency breakdown
  - **Micrometer/JVM/Spring Boot**: HTTP requests, JVM memory, threads, GC, process CPU
- **Note**: Select the Prometheus datasource when importing. Use in-product search for "Node Exporter Full", "cAdvisor", "Blackbox Exporter", "Micrometer/JVM" (exact IDs may vary).

### 3. Auto-Refresh & Granularity
- Set dashboard refresh to 5–10s.
- Use reasonable time ranges (e.g., Last 15m).
- If Prometheus scrape intervals are tuned (e.g., 2s for app/blackbox/cadvisor), panels update within seconds.

## Load Testing

### Option A: Use the Menu Script
```bash
./cli_live_dashboard.sh
```
- Choose: Low/High/Memory/DB/Combined load tests, or reset metrics/DB.

### Option B: Use k6 (Inside Compose Network)
```bash
# Smoke Test
docker compose run --rm -e APP_URL=http://app:8080 k6 run k6/smoke.js

# Staged Stress Test
docker compose run --rm -e APP_URL=http://app:8080 k6 run k6/stress.js
```

### What to Watch During Load Tests
- **Grafana**: SRE Overview (p50/p95, error %, container CPU/Mem, uptime)
- **Prometheus**: Alerts (thresholds from Step 4)
- **Email**: Alertmanager via SES (High CPU/Mem, Slow health, AppDown)

## Hardening Notes

- **Resource Caps**: App container limited (e.g., `mem_limit: 1024m`, `cpus: "1.0"`)
- **Probes**: Actuator readiness/liveness exposed; compose healthcheck uses readiness
- **Timeouts & Pools**: Modest Tomcat thread/pool sizes, Hikari connection timeout
- **Non-root Alertmanager**: Custom image with env-rendered config (no baked secrets)
- **IST Time**: Enforced across app logs and DB (`timestamptz`)
- **Repeatable Tests**: k6 thresholds fail runs if SLOs regress (CI-friendly)

## Why This Matters

You can now validate the entire observability loop:
- **Generate Load**: k6 or `cli_live_dashboard.sh`
- **Watch Metrics**: Prometheus/Grafana dashboards
- **Receive Alerts**: Alertmanager/SES emails
- **Inspect DB Snapshots**: App writes stats every 5s
- **Reason About**: Latency, errors, container pressure, host health, uptime
