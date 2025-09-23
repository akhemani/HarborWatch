# HarborWatch — Step 4: Central Scrape + Alerting (Core App + Postgres + Health + LoadServer + DebugAPIs + Background Scheduler + Prometheus + Alertmanager + Blackbox + SES)

In this **Step 4**: Prometheus scrapes app, container, and host metrics; Blackbox actively probes health/latency; and Alertmanager sends real emails via AWS SES (SMTP). Dashboards (Grafana) are planned for Step 5.

## What's Included (Up to Step 4)
- ✅ **Step 1**: Core app + Postgres + Health endpoint (`/actuator/health`)
- ✅ **Step 2**: Load endpoints + 5s scheduler + clear logs (IST)
  - Tables: `performance_data`, `computation_results`
- ✅ **Step 3**: Metrics exposure (no dashboards)
  - `/actuator/prometheus`, cAdvisor (container metrics), node_exporter (host metrics)
- ✅ **Step 4**: Prometheus + Blackbox + Alertmanager
  - Central scrape
  - Alert rules: CPU > 40%, Memory > 50%, Health latency > 1s, App down
  - Emails via SES SMTP
- ✅ Menu script (`cli_live_dashboard.sh`) to drive load & print metric/log snapshots

## Repository Layout
```plaintext
harborwatch/
├── docker-compose.yml
├── Dockerfile
├── cli_live_dashboard.sh
├── README.md
├── pom.xml
├── monitoring/
│   ├── prometheus.yml        # Prometheus scrape config
│   ├── alerts.yml           # Prometheus alert rules
│   ├── blackbox.yml         # Blackbox module (http_2xx)
│   ├── Alertmanager.Dockerfile  # Custom AM image
│   ├── alertmanager-run.sh   # Renders template with env + starts AM
│   └── alertmanager.yml.tmpl # Alertmanager config template (envsubst)
└── src/
    └── main/...
```
*Note*: Ensure filenames match `docker-compose.yml`: `Alertmanager.Dockerfile`, `alertmanager-run.sh`, `alertmanager.yml.tmpl`.

## Services (Docker Compose)
The stack runs 7 services:
- **db**: Postgres 15 (`appdb`, user: `postgres`, pass: `postgres123`, IST)
- **app**: Spring Boot service (JVM TZ IST)
  - **Load Endpoints**:
    - `/api/cpu-intensive`
    - `/api/memory-intensive`
    - `/api/database-intensive`
    - `/api/combined-stress`
  - **Debug Endpoints (dev-only)**:
    - `/api/debug/summary`
    - `/api/debug/performance/recent`
    - `/api/debug/computations/recent`
    - `/api/debug/now`
  - **Actuator**:
    - `/actuator/health`
    - `/actuator/prometheus`
- **cadvisor**: Container metrics + UI (`http://localhost:8085`)
- **node_exporter**: Host metrics (`http://localhost:9100/metrics`)
- **blackbox**: Active HTTP probe for app health/latency
- **prometheus**: Central scrape + alerts (`http://localhost:9090`)
- **alertmanager**: Routing/notifications via AWS SES SMTP (`http://localhost:9093`)
  - Custom image: renders config from template with env vars at startup

The updated `docker-compose.yml` includes all services, with tuned cAdvisor housekeeping flags and the custom Alertmanager build.

## Alerting Thresholds (PromQL)
Defined in `monitoring/alerts.yml`:
- **Container CPU > 40% for 2m**:
  ```promql
  rate(container_cpu_usage_seconds_total{name="hw-app"}[2m]) > 0.40
  ```
- **Container Memory > 50% for 5m**:
  ```promql
  (container_memory_working_set_bytes{name="hw-app"} / container_spec_memory_limit_bytes{name="hw-app"}) * 100 > 50
  ```
- **Health latency > 1s for 3m (Blackbox)**:
  ```promql
  avg_over_time(probe_duration_seconds{job="blackbox-http",instance="http://app:8080/actuator/health"}[3m]) > 1
  ```
- **App down for 1m (Blackbox)**:
  ```promql
  probe_success{job="blackbox-http",instance="http://app:8080/actuator/health"} == 0
  ```

If cAdvisor labels differ, inspect `container_cpu_usage_seconds_total` in Prometheus → Graph to find the correct label (e.g., `name="hw-app"`).

## Email Delivery (Alertmanager → SES SMTP)
A custom Alertmanager image renders `alertmanager.yml` from a template using environment variables at runtime:
- **Template**: `monitoring/alertmanager.yml.tmpl`
- **Entrypoint**: `monitoring/alertmanager-run.sh` (uses `envsubst`, logs first 20 lines for sanity)
- **Dockerfile**: `monitoring/Alertmanager.Dockerfile` (alpine, non-root user, copies binaries from Prometheus release tarball)

### Required `.env` File
Create a `.env` file in the repo root (loaded automatically by Compose):
```plaintext
# Sender (must be verified in SES if sandbox)
ALERT_FROM=monitoring@yourdomain.com
# Comma-separated recipients (must be verified if SES in sandbox)
ALERT_TO=you@yourdomain.com,team@yourdomain.com
# SES region and SMTP creds (NOT AWS access keys)
SES_REGION=ap-south-1
SES_SMTP_USER=AKIAXXXXXXXXEXAMPLE
SES_SMTP_PASS=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
```
Obtain SMTP credentials from SES Console → SMTP Settings. If SES is in sandbox mode, verify `ALERT_FROM` and recipients, or request production access.

### Rendered Alertmanager Config
```yaml
global:
  smtp_smarthost: email-smtp.ap-south-1.amazonaws.com:587
  smtp_from: monitoring@yourdomain.com
  smtp_auth_username: AKIAXXX...
  smtp_auth_password: xxxxx...
  smtp_require_tls: true
route:
  receiver: team-email
  group_wait: 60s
  group_interval: 5m
  repeat_interval: 2h
receivers:
  - name: team-email
    email_configs:
      - to: you@yourdomain.com,team@yourdomain.com
```

## Build & Run
1. Build the app (if changed):
   ```bash
   ./mvnw -q -DskipTests package
   ```
2. Bring everything up (builds custom Alertmanager image):
   ```bash
   docker compose up -d --build
   ```
3. Verify targets:
   - Prometheus UI: `http://localhost:9090` → Status → Targets (all "UP")
   - Alertmanager UI: `http://localhost:9093` → Status → "Config" (confirm rendered settings)
   - cAdvisor UI: `http://localhost:8085`
   - App metrics: `http://localhost:8080/actuator/prometheus`
   - Host metrics: `http://localhost:9100/metrics`

Alertmanager container logs will show:
```
[am] Rendered /etc/alertmanager/alertmanager.yml
----- rendered config (first lines) -----
...
-----------------------------------------
```

## Generate Alerts (Manual Tests)
Use the menu script `./cli_live_dashboard.sh` (recommended) or plain `curl`:

- **High CPU (2m)**:
  ```bash
  curl "http://localhost:8080/api/cpu-intensive?iterations=5000000"
  ```
  Or menu: `2) High CPU` / `5) Combined (heavy)`
  - Prometheus → Alerts: `ContainerHighCPU` → Pending → Firing → email sent

- **High Memory (5m)**:
  ```bash
  curl "http://localhost:8080/api/memory-intensive?sizeMb=700"
  ```
  (Assuming app memory limit ≈ 1024M; adjust size or repeat calls.)
  - Checks `ContainerHighMemory`

- **Slow Health (3m)**:
  ```bash
  curl "http://localhost:8080/api/combined-stress?durationSec=120"
  ```
  - Checks `AppHealthSlow` via Blackbox probe duration

- **App Down (1m)**:
  ```bash
  docker stop hw-app
  # wait ~1m → "AppDown" alert → email
  docker start hw-app
  # alert resolves, Alertmanager shows "Resolved"
  ```

## Useful URLs
- App: `http://localhost:8080`
  - Health: `/actuator/health`
  - Metrics: `/actuator/prometheus`
- Prometheus: `http://localhost:9090`
  - Status → Targets (scrape health)
  - Alerts (active/disabled/firing)
  - Graph (ad-hoc queries)
- Alertmanager: `http://localhost:9093`
- cAdvisor: `http://localhost:8085`
- node_exporter: `http://localhost:9100/metrics`

## Troubleshooting
- **No Emails**:
  - Alertmanager UI → Status (Config loaded?) / Alerts (firing?)
  - `docker logs -f hw-alertmanager` (check SMTP errors)
  - SES sandbox requires verified `ALERT_FROM` and recipients
- **Prometheus “DOWN” Targets**:
  - Prometheus UI → Status → Targets → check errors
  - Ensure Compose service DNS names match configs: `app:8080`, `cadvisor:8080`, `node_exporter:9100`, `blackbox:9115`
- **CPU/Memory Alerts Not Firing**:
  - Labels may differ. Inspect in Prometheus:
    ```promql
    label_values(container_cpu_usage_seconds_total, name)
    ```
  - Update `alerts.yml` with correct `name="..."`.
  - Memory alert needs a container memory limit. Confirm in Prometheus:
    ```promql
    container_spec_memory_limit_bytes{name="hw-app"}
    container_memory_working_set_bytes{name="hw-app"}
    ```
- **Alert Storming**:
  - Tune Alertmanager `group_wait`, `group_interval`, `repeat_interval`
  - Raise rule `for` windows (e.g., 3–10m) to avoid flapping

## Security & Ops Notes
- Alertmanager runs non-root and renders config via `envsubst` (no creds baked into image)
- Keep `.env` out of source control; use Docker secrets or a credentials manager in production
- Prometheus TSDB retention is 7 days (set in `prometheus` args); tune as needed