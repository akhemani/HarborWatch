# HarborWatch — Container Monitoring System

HarborWatch is a production-style container monitoring lab you can run locally. It evolves a simple Spring Boot application with Postgres into a full observability stack featuring Prometheus, Alertmanager, Grafana, exporters (cAdvisor, node_exporter, blackbox), and repeatable load testing with k6. All timestamps and logs are set to IST (Asia/Kolkata).

## What You Get (End State)

- **App**: Spring Boot with Actuator, Micrometer (Prometheus registry), health probes, realistic load endpoints, and a 5-second scheduler writing app-local snapshots to Postgres.
- **DB**: Postgres 15 with Flyway migrations (`performance_data`, `computation_results`).
- **Exporters**:
  - cAdvisor: Container CPU/Mem/IO + simple UI
  - node_exporter: Host metrics
  - blackbox-exporter: Active HTTP probes for up/down + latency
- **Collection & Alerting**: Prometheus (central scrape + alert rules) → Alertmanager (emails via SES SMTP).
- **Dashboards**: Grafana with auto-provisioned Prometheus datasource, SRE Overview dashboard, and guidance for importing Node Exporter, cAdvisor, Blackbox, and Micrometer/JVM dashboards.
- **Load Testing**: k6 scripts (`smoke.js`, `stress.js`) + optional `cli_live_dashboard.sh` CLI menu for load and snapshots.
- **Hardening**:
  - Resource caps (memory/CPU)
  - Liveness/readiness probes
  - Modest timeouts/pools
  - Non-root Alertmanager image
  - Templated secrets
  - IST enforced everywhere

## Repository Layout

```plaintext
harborwatch/
├─ docker-compose.yml                   # Full stack definition
├─ Dockerfile                          # App container
├─ README.md                           # This file (main branch)
├─ pom.xml                             # Maven build
├─ cli_live_dashboard.sh                      # Optional CLI menu: stress + snapshots
├─ k6/
│  ├─ smoke.js                        # Smoke test script
│  └─ stress.js                       # Staged stress test script
├─ monitoring/
│  ├─ prometheus.yml                  # Prometheus scrape config
│  ├─ alerts.yml                      # Alert rules
│  ├─ blackbox.yml                    # Blackbox probe modules
│  ├─ grafana/
│  │  ├─ provisioning/
│  │  │  ├─ datasources/datasource.yml # Prometheus datasource
│  │  │  └─ dashboards/dashboards.yml  # Dashboard provisioning
│  │  └─ dashboards/sre-overview.json  # SRE Overview dashboard
│  ├─ Alertmanager.Dockerfile         # Non-root Alertmanager image
│  ├─ alertmanager-run.sh             # Renders Alertmanager config
│  └─ alertmanager.yml.tmpl           # Alertmanager config template
└─ src/
   └─ main/
      ├─ java/dev/harborwatch/
      │  ├─ HarborWatchApplication.java  # Main app with @EnableScheduling
      │  ├─ load/LoadService.java        # CPU/Mem/DB/Combined logic
      │  ├─ load/LoadController.java     # /api/* endpoints
      │  ├─ load/MetricsScheduler.java   # 5s snapshots to performance_data
      │  └─ debug/*.java                 # Read-only debug utilities
      └─ resources/
         ├─ application.yml              # DB, Actuator, Micrometer, IST logging
         ├─ logback-spring.xml          # Human-friendly logs, IST timestamps
         └─ db/migration/
            ├─ V1__init.sql             # Initial DB schema
            └─ V2__ist_timezone_and_types.sql # IST and timestamptz setup
```

## Services & URLs

| Service         | Purpose                            | URL                                      |
|-----------------|------------------------------------|------------------------------------------|
| app             | Spring Boot + Actuator/Metrics     | [http://localhost:8080](http://localhost:8080) |
| app metrics     | Prometheus text                    | [http://localhost:8080/actuator/prometheus](http://localhost:8080/actuator/prometheus) |
| health          | Liveness/Readiness                 | `/actuator/health`, `/health/readiness`  |
| db              | Postgres 15                        | Exposed on 5432 (local tools)            |
| prometheus      | Scrape + TSDB + rules              | [http://localhost:9090](http://localhost:9090) |
| alertmanager    | Routing/notifications (SES SMTP)   | [http://localhost:9093](http://localhost:9093) |
| cadvisor        | Container metrics + UI             | [http://localhost:8085](http://localhost:8085) |
| node_exporter   | Host metrics (text)                | [http://localhost:9100/metrics](http://localhost:9100/metrics) |
| blackbox        | Active probe (Prom via /probe)     | [http://localhost:9115](http://localhost:9115) |
| grafana         | Dashboards                         | [http://localhost:3000](http://localhost:3000) (admin/admin) |

**Note**: Compose service DNS names (e.g., `app:8080`, `prometheus:9090`) are used inside the network.

## Build & Run

1. **Build And Start**:
   ```bash
   docker compose up -d --build
   ```

2. **Verify**:
   - Prometheus: [Status → Targets](http://localhost:9090) (all UP)
   - Alertmanager: [Status → Config](http://localhost:9093) (rendered template)
   - cAdvisor UI: [http://localhost:8085](http://localhost:8085) (click `hw-app` for live metrics)

## Endpoints (App)

### Load / Stress
- `GET /api/cpu-intensive?iterations=1000000`
- `GET /api/memory-intensive?sizeMb=50`
- `GET /api/database-intensive?ops=1000`
- `GET /api/combined-stress?durationSec=20`

### Debug (Read-Only)
- `GET /api/debug/summary` – Row counts
- `GET /api/debug/performance/recent` – Last 10 in `performance_data`
- `GET /api/debug/computations/recent` – Last 10 in `computation_results`
- `GET /api/debug/now` – Compare app clock, DB `now()`, and latest rows

### Health & Metrics
- `GET /actuator/health` (+ `/readiness`, `/liveness`)
- `GET /actuator/prometheus`

## Dashboards (Grafana)

### Included: HarborWatch — SRE Overview
- **Panels**:
  - App latency (p50/p95): Micrometer histogram
  - App error rate (%): 5xx / total requests
  - Container CPU (cores): cAdvisor `rate()`
  - Container Memory (%): `working_set / limit`
  - Uptime (binary): Blackbox `probe_success`
  - Quick link to Alertmanager

### Import Recommended Dashboards
- **Location**: Grafana → Dashboards → Import
- **Search for**:
  - Node Exporter: Host CPU/mem/disk/network
  - cAdvisor/Docker: Per-container CPU/Mem/IO
  - Blackbox Exporter: Uptime + latency
  - Micrometer/JVM/Spring Boot: HTTP, JVM GC/mem/threads, process CPU
- **Note**: Select Prometheus datasource. Set refresh to 5–10s, time range to Last 15m.

### Optional: Postgres Exporter
- Add `postgres_exporter` service to `docker-compose.yml`.
- Configure Prometheus to scrape it.
- Import a Postgres dashboard in Grafana for DB internals (connections, cache hit ratio, etc.).

## Alerts (Prometheus → Alertmanager → SES)

### Rules (`monitoring/alerts.yml`, tune as needed):
- **Container CPU**: `rate(container_cpu_usage_seconds_total{name="hw-app"}[2m]) > 0.40` for 2m
- **Container Memory**: `100 * working_set / limit > 50` for 5m
- **Health Latency**: `avg_over_time(probe_duration_seconds[3m]) > 1` for 3m
- **App Down**: `probe_success == 0` for 1m

### Email Delivery (SES SMTP)
- Create `.env` in repo root (loaded by Compose):
  ```plaintext
  ALERT_FROM=monitoring@yourdomain.com
  ALERT_TO=you@yourdomain.com,team@yourdomain.com
  SES_REGION=ap-south-1
  SES_SMTP_USER=AKIAXXXXXXX
  SES_SMTP_PASS=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
  ```
- In SES sandbox, verify sender and recipients or request production access.
- Alertmanager config rendered from `monitoring/alertmanager.yml.tmpl` via `alertmanager-run.sh` (non-root image).

## Load Testing

### Option A: Menu (Human-Friendly)
```bash
./cli_live_dashboard.sh
```
- Choose: Low/High/Memory/DB/Combined
- View app metrics, host metrics, and logs after each round

### Option B: k6 (CI-Friendly)
```bash
# Smoke Test
docker compose run --rm -e APP_URL=http://app:8080 k6 run k6/smoke.js

# Staged Stress Test
docker compose run --rm -e APP_URL=http://app:8080 k6 run k6/stress.js
```
- Watch: Grafana (SRE Overview), Prometheus → Alerts, SES email notifications

## Production-Style Touches

- **Resource Caps**: Memory/CPU limits for meaningful container % metrics
- **Probes**: Actuator liveness/readiness; Compose healthcheck uses readiness
- **Micrometer Histogram**: Enables p50/p95 latency panels
- **Timeouts/Pools**: Modest Tomcat threads, Hikari timeouts
- **Non-root Alertmanager**: Templated SMTP creds (no baked secrets)
- **IST Everywhere**: JVM, logs, DB `timestamptz`
- **Repeatable Tests**: k6 thresholds fail on SLO regressions

## Troubleshooting

- **Prometheus Target DOWN**:
  - Check [Prometheus → Status → Targets](http://localhost:9090)
  - Verify DNS names: `app:8080`, `cadvisor:8080`, `node_exporter:9100`, `blackbox:9115`
- **No Grafana Data**:
  - Check time range, auto-refresh, Prometheus status, query errors
- **CPU/Memory Alert Not Firing**:
  - Verify `name="hw-app"` in cAdvisor labels:
    ```promql
    label_values(container_cpu_usage_seconds_total, name)
    ```
  - Ensure container memory limit is set
- **No Emails**:
  - Check Alertmanager logs, SES region/SMTP creds, sandbox verification
- **IST Mismatch**:
  - App logs should show IST (+05:30)
  - DB: `docker exec -it hw-db psql -U postgres -d appdb -c "SHOW TIMEZONE;"`
  - Migrations enforce `timestamptz`

## Learning Roadmap

HarborWatch evolves through five steps:
1. Core app + Postgres + health endpoints
2. Load endpoints + scheduler + clear logs
3. Metrics exposure (Actuator, cAdvisor, node_exporter)
4. Central scrape + alerting (Prometheus, Alertmanager, blackbox, SES)
5. Load testing + visualization + hardening (k6, Grafana, limits/probes)

The `main` branch contains the final stack. Recreate earlier steps by removing services/files or checking earlier branches (if preserved).

## Quick Commands

- **Rebuild App + Restart**:
  ```bash
  docker compose up -d --build app
  ```

- **Tail App Logs** (IST, human-friendly):
  ```bash
  docker logs -f hw-app
  ```

- **DB Checks**:
  ```bash
  docker exec -it hw-db psql -U postgres -d appdb -c "SELECT COUNT(*) FROM performance_data;"
  docker exec -it hw-db psql -U postgres -d appdb -c "SELECT now(); SHOW TIMEZONE;"
  ```

- **Prometheus Query**:
  - Test in-browser: [http://localhost:9090/graph?g0.expr=probe_success](http://localhost:9090/graph?g0.expr=probe_success)

- **Alertmanager UI**: [http://localhost:9093](http://localhost:9093)

- **Grafana**: [http://localhost:3000](http://localhost:3000) (admin/admin)

## License

MIT — use freely; attribution appreciated.