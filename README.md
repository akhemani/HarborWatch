# HarborWatch — Step 2 (Core App + Postgres + Health + LoadServer + DebugAPIs + Background Scheduler)

This repository includes Steps 1–3 of the HarborWatch project, providing a Spring Boot application with stress testing capabilities, metrics exposure, and a Bash automation script (`cli_live_dashboard.sh`) for running tests and monitoring.

## Table of Contents
- [What's Included (Steps 1–3)](#whats-included-steps-1–3)
- [Repo Structure](#repo-structure)
- [Services (Docker Compose)](#services-docker-compose)
- [Database Schema](#database-schema)
- [Build and Run](#build-and-run)
- [Endpoints (Step 3)](#endpoints-step-3)
- [Load / Stress (Step 2)](#load--stress-step-2)
- [Debug (Step 2 — Dev-Only)](#debug-step-2--dev-only)
- [Metrics (Step 3)](#metrics-step-3)
- [Automation Script (cli_live_dashboard.sh)](#automation-script-cli_live_dashboard)
  - [Overview](#overview)
  - [How to Run](#how-to-run)
  - [Menu Options](#menu-options)
  - [Output After Each Run](#output-after-each-run)
  - [Prerequisites](#prerequisites)
  - [Troubleshooting](#troubleshooting)
  - [Tips](#tips)
 

## What's Included (Steps 1–3)

- ✅ **Step 1: Core App + Postgres + Health**
  - Spring Boot service with `/actuator/health`
  - Postgres with Flyway migrations
  - Schema: `performance_data`, `computation_results`
  - Docker Compose: `db` + `app`

- ✅ **Step 2: Load Endpoints + Background Jobs + Clear Logs**
  - Endpoints to create CPU, memory, and database load
  - `@Scheduled` job (every 5s) writing metrics to DB
  - Logs to stdout (no log files), human-readable, IST timestamps
  - Optional debug endpoints to inspect DB state

- ✅ **Step 3: Metrics Exposure (No Dashboards)**
  - `/actuator/prometheus` (Micrometer + Prometheus registry)
  - cAdvisor (container metrics/UI)
  - `node_exporter` (host metrics)

- ✅ **Automation Script (`cli_live_dashboard.sh`)**
  - Menu-driven stress tests + metrics snapshots + last logs
  - Options to reset app metrics and/or truncate DB tables

## Repo Structure

```text
harborwatch/
  ├─ docker-compose.yml             # db + app + cadvisor + node_exporter
  ├─ Dockerfile                     # Java 21 JRE base, non-root
  ├─ README.md                      # this file
  ├─ cli_live_dashboard.sh                 # menu script (stress + metrics + logs)
  ├─ pom.xml
  └─ src/
     └─ main/
        ├─ java/dev/harborwatch/
        │  ├─ HarborWatchApplication.java      # @EnableScheduling
        │  ├─ load/LoadService.java            # CPU/Mem/DB/Combined logic
        │  ├─ load/LoadController.java         # /api/* endpoints
        │  ├─ load/MetricsScheduler.java       # 5s tick → performance_data (IST)
        │  └─ debug/
        │     ├─ DebugController.java          # recent rows & counts
        │     └─ ClockDebugController.java     # app vs DB time & last rows
        └─ resources/
           ├─ application.yml                  # DB, Actuator, Micrometer, logging
           ├─ logback-spring.xml               # plain text logs in IST
           └─ db/migration/
              ├─ V1__init.sql                  # base schema
              └─ V2__ist_timezone_and_types.sql # timestamptz + Asia/Kolkata
```

## Services (Docker Compose)

- **db** — Postgres 15
  - Health check: `pg_isready`
  - TZ env: `TZ=Asia/Kolkata`, `PGTZ=Asia/Kolkata`
  - DB: `appdb` / user: `postgres` / pass: `postgres123`

- **app** — Spring Boot service
  - Exposes:
    - `GET /actuator/health`
    - `GET /actuator/prometheus`
    - `GET /api/...` (load endpoints)
    - `GET /api/debug/...` (dev only)
  - JVM TZ: `-Duser.timezone=Asia/Kolkata`
  - Logs: stdout, IST timestamps

- **cadvisor** — Container metrics + simple UI
  - UI: `http://localhost:8085`

- **node_exporter** — Host metrics
  - Text metrics on `http://localhost:9100` (host network mode)

## Database Schema

- **performance_data**
  - Written by:
    - Scheduler (every 5s): `cpu_load`, `memory_usage`, `request_count`, `error_rate`
    - `/api/database-intensive`: many `test_metric_*` rows per run

- **computation_results**
  - Written by:
    - `/api/cpu-intensive`
    - `/api/database-intensive`
    - `/api/combined-stress`

## Build and Run

Run the following command to build and start the services:

```bash
docker compose up -d --build
```

## Endpoints (Step 3)

- **App Base URL**: `http://localhost:8080`
- **Actuator Health**: `http://localhost:8080/actuator/health`
- **Prometheus Exporter**: `http://localhost:8080/actuator/prometheus`
- **cAdvisor UI**: `http://localhost:8085`
- **node_exporter Metrics**: `http://localhost:9100/metrics`

## Load / Stress (Step 2)

- `GET /api/cpu-intensive?iterations=1000000`
- `GET /api/memory-intensive?sizeMb=50`
- `GET /api/database-intensive?ops=1000`
- `GET /api/combined-stress?durationSec=20`

## Debug (Step 2 — Dev-Only)

- `GET /api/debug/summary` — Row counts in both tables
- `GET /api/debug/performance/recent` — Last 10 `performance_data` rows
- `GET /api/debug/computations/recent` — Last 10 `computation_results` rows
- `GET /api/debug/now` — Compare app clock, DB clock, and latest rows

## Metrics (Step 3)

- **Prometheus**: `GET /actuator/prometheus` — App metrics (JVM, threads, GC, `http.server.requests`, etc.)
- **cAdvisor**: `http://localhost:8085` — Container CPU, memory, filesystem, and I/O per container
- **node_exporter**: `http://localhost:9100/metrics` — Host CPU, memory, disk, and network metrics

## Automation Script (cli_live_dashboard.sh)

### Overview
The `cli_live_dashboard.sh` script automates stress testing for your application by running various test patterns (CPU, memory, database, or combined) and outputs key metrics and logs. It integrates with Docker, Prometheus, and cAdvisor for monitoring and is designed to be run at the root of your repository.

### How to Run
1. Place the script at the repository root as `cli_live_dashboard.sh`.
2. Make it executable:
   ```bash
   chmod +x cli_live_dashboard.sh
   ```
3. Run the script:
   ```bash
   ./cli_live_dashboard.sh
   ```

### Menu Options
The script provides the following menu options for stress testing:

- **Low CPU**: Burst iterations=200,000
- **High CPU**: Burst iterations=5,000,000
- **Memory**: Burst sizeMb=100
- **Database**: Burst ops=800
- **Combined (heavy)**: Parallel combined stress + interleaved CPU/memory bursts
- **Reset metrics**: Restart app container (resets Actuator counters; DB unchanged)
- **Reset metrics + wipe DB tables**: Restart app + TRUNCATE both tables
- **Exit**: Exit the script

### Output After Each Run
After each test run, the script outputs:

#### App Metrics (from `/actuator/prometheus`)
- `process_cpu_usage`
- Aggregated `http_server_requests_seconds`:
  - Count
  - Sum
  - Max
  - Average (for the chosen URI)

#### Host Metrics (from `node_exporter` on `:9100`)
- Per-CPU time spent in:
  - Idle
  - Iowait
  - IRQ
  - Nice
  - Softirq
  (Shows first 16 CPUs by default)
- `MemAvailable_bytes` (displayed in GiB)

#### Logs
- Last 8 application logs (stdout tail of `hw-app`)

### Prerequisites
- **Docker stack**: Ensure the stack is running:
  ```bash
  docker compose up -d
  ```
- **Dependencies**: The script uses `bash`, `curl`, `awk`, and `grep`.
- **Prometheus Actuator**: Ensure `micrometer-registry-prometheus` is included in `pom.xml` and configured in `application.yml`:
  ```yaml
  management:
    endpoints:
      web:
        exposure:
          include: health,info,prometheus
  ```

### Troubleshooting
#### No New Scheduler Rows
- Rebuild and restart the application:
  ```bash
  ./mvnw -q -DskipTests package && docker compose up -d --build
  ```
- Check logs for scheduler tick `START/DONE` lines:
  ```bash
  docker logs -f hw-app
  ```

#### DB Time Incorrect
- Verify the database time:
  ```sql
  SELECT now();
  ```

#### `/actuator/prometheus` Missing
- Ensure `micrometer-registry-prometheus` is in `pom.xml`.
- Verify `application.yml` configuration (see [Prerequisites](#prerequisites)).

#### cAdvisor UI Not Loading
- Visit `http://localhost:8085`.
- Check Docker volumes and permissions (Linux requires `/sys` and `/var/run/docker.sock` binds).

#### High Resource Usage
- Reduce the following parameters in the script or your `curl` tests:
  - Iterations
  - `sizeMb`
  - `ops`
  - `durationSec`

### Tips
- **View all CPUs**: Run the script with:
  ```bash
  MAX_CPUS=999 ./cli_live_dashboard.sh
  ```
- **Monitor with cAdvisor**: Open `http://localhost:8085` and click the `hw-app` container to monitor resource usage during tests.

