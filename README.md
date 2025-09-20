# HarborWatch — Step 2 (Core app + Postgres + Health + LoadServer + DebugAPIs + Background Scheduler)
This repo currently includes Step 1 (Core App + Postgres + Health) and Step 2 (Load Endpoints + Background Jobs + Sane Logs with IST timestamps).

 # What’s here (Step 1 & Step 2)

✅ Spring Boot app with /actuator/health.

✅ PostgreSQL with Flyway migrations (schema managed, no ad-hoc DDL).

✅ Two tables:

  * performance_data — periodic metrics & test metrics.
  * computation_results — summaries of load runs.

✅ Load APIs to create real CPU / Memory / DB pressure:

  * GET /api/cpu-intensive?iterations=...
  * GET /api/memory-intensive?sizeMb=...
  * GET /api/database-intensive?ops=...
  * GET /api/combined-stress?durationSec=...

✅ Background scheduler (every 5s) writes 4 sample metrics to the DB.

✅ Clear, container-friendly logs to stdout (no files) with IST timestamps.

✅ Debug endpoints to view DB state without SQL:

  * GET /api/debug/summary
  * GET /api/debug/performance/recent
  * GET /api/debug/computations/recent
  * GET /api/debug/now (compare App clock, DB clock, last rows)

Important: Logs are not stored in the DB (production best practice).
DB holds data (metrics & run summaries). Logs go to container stdout and later to a log pipeline.

# Repo Layout

```text
harborwatch/
  ├─ docker-compose.yml          # db + app (Step 1 & 2)
  ├─ Dockerfile                  # builds the app image (non-root, JRE 21)
  ├─ README.md                   # this file
  ├─ pom.xml                     # Step 1 & 2 deps
  └─ src/
     ├─ main/java/dev/harborwatch/...
     │  ├─ HarborWatchApplication.java     # @EnableScheduling
     │  ├─ load/LoadService.java           # CPU/Mem/DB/Combined logic
     │  ├─ load/LoadController.java        # /api/* endpoints
     │  ├─ load/MetricsScheduler.java      # 5s tick → performance_data
     │  └─ debug/*.java                    # read-only debug endpoints
     └─ main/resources/
        ├─ application.yml                 # DB + logging levels + Hibernate TZ
        ├─ logback-spring.xml              # plain, human-friendly logs in IST
        └─ db/migration/
           ├─ V1__init.sql                 # base schema
           └─ V2__ist_timezone_and_types.sql
```


# Build and Run
 * docker compose up -d --build

# Endpoints (Step 2)
# Load / Stress

* GET /api/cpu-intensive?iterations=1000000
  * Runs a CPU-bound loop; caps applied to protect your machine.
  * Persists a summary row to computation_results.

* GET /api/memory-intensive?sizeMb=50
  * Allocates/touches memory; returns duration and checksum.

* GET /api/database-intensive?ops=1000
  * Inserts many rows into performance_data + periodic reads.
  * Persists a summary row to computation_results.

* GET /api/combined-stress?durationSec=20
  * Parallel mix of CPU/Mem/DB operations for N seconds.
  * Persists a summary row to computation_results.

# Debug (read-only)

* GET /api/debug/summary
  * Total counts in both tables.

* GET /api/debug/performance/recent
  * Last 10 rows from performance_data.

* GET /api/debug/computations/recent
  * Last 10 rows from computation_results.

* GET /api/debug/now
  * Compares App clock (IST), DB now() (IST), and last rows from both tables.

# Health

* GET /actuator/health → {"status":"UP"}

# Quick tests
# Hit the load endpoints

* curl "http://localhost:8080/api/cpu-intensive?iterations=2000000"
* curl "http://localhost:8080/api/memory-intensive?sizeMb=50"
* curl "http://localhost:8080/api/database-intensive?ops=1000"
* curl "http://localhost:8080/api/combined-stress?durationSec=20"


# Watch logs (human-readable, IST)

* docker logs -f hw-app
  * Example lines every ~5s:
  * ... MetricsScheduler - scheduler tick START appTimeIST=2025-09-20T15:51:05.123+05:30[Asia/Kolkata]
  * ... MetricsScheduler - scheduler tick DONE cpu_load=41.3 memory_usage=52.8 request_count=309 error_rate=7.1

# Inspect DB directly

# counts
* docker exec -it hw-db psql -U postgres -d appdb -c "SELECT COUNT(*) FROM performance_data;"
* docker exec -it hw-db psql -U postgres -d appdb -c "SELECT COUNT(*) FROM computation_results;"

# recent rows (IST; look for +05:30)
* docker exec -it hw-db psql -U postgres -d appdb -c \
"SELECT id, timestamp, metric_name, metric_value, metadata FROM performance_data ORDER BY id DESC LIMIT 10;"

* docker exec -it hw-db psql -U postgres -d appdb -c \
"SELECT id, timestamp, computation_type, input_size, duration_ms FROM computation_results ORDER BY id DESC LIMIT 10;"
