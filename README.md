# HarborWatch — Step 1 (Core app + Postgres + Health)

A minimal Spring Boot service wired to PostgreSQL with Flyway migrations and Actuator health endpoints.  
This is **Step 1** of a 5-step roadmap. No exporters, Prometheus, or Grafana yet — just the core app and DB.

---

## What’s in this step

- Spring Boot (Web, Actuator, Data JPA) + Flyway
- PostgreSQL container with healthcheck
- App waits for DB health, runs migrations, and exposes `/actuator/health`
- Container-friendly logging to `stdout` (visible via `docker logs`)

---

## Prerequisites

- **Docker** (Desktop or Engine) installed and running
- **No local Maven required** — the build uses Maven **inside Docker**

> If you have local Java/Maven, you can also run `./mvnw package` (Maven Wrapper).  
> If not, then use

docker run --rm \
  -v "$PWD":/workspace -w /workspace \
  -v "$HOME/.m2":/root/.m2 \
  maven:3.9-eclipse-temurin-21 \
  mvn -q -DskipTests package


> This README shows Dockerized Maven so nothing else is needed locally.

---

## Project layout

harborwatch/
├─ docker-compose.yml
├─ Dockerfile
├─ pom.xml
├─ README.md
└─ src/
└─ main/
├─ java/dev/harborwatch/HarborWatchApplication.java
└─ resources/
├─ application.yml
└─ db/migration/V1__init.sql