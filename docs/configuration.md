# Configuration Reference

Spring Boot profile YAML files live in `application/src/main/resources/`. The base
`application.yaml` sets defaults; profile-specific files layer on top of it.

---

## Postgres image version

The Postgres Docker image tag used by local development (`docker-compose.yml`)
and by Testcontainers in integration tests is declared once, in `POSTGRES_VERSION`
in the repository-root `.env` file. `docker-compose.yml` substitutes it directly;
`application/build.gradle.kts` reads the same file at configuration time and
passes it to the test JVM as the `postgres.version` system property, which
`AbstractIntegrationTest` uses to build the Testcontainers image tag. Bump the
version in `.env` only — no other file needs to change.

---

## Profiles

| Profile | Purpose | Activate with |
|---------|---------|---------------|
| *(default)* | Local development — local PostgreSQL, OTel export disabled | *(no flag needed)* |
| `test` | Used by the integration test suite; `AbstractIntegrationTest` sets this via `@ActiveProfiles` | set automatically by tests |
| `otel` | Enables OpenTelemetry OTLP export (traces, metrics, logs) to the collector started by `docker-compose.yml` | `SPRING_PROFILES_ACTIVE=otel` |

Add new profiles to this table (and their `${ENV_VAR}` placeholders below) in the
same commit that introduces them — see the Configuration rule in `agent.md`.

---

## Environment Variables

### OpenTelemetry (`otel` profile)

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `OTEL_METRICS_URL` | No | `http://localhost:4318/v1/metrics` | OTLP HTTP endpoint for metrics |
| `OTEL_TRACES_URL` | No | `http://localhost:4318/v1/traces` | OTLP HTTP endpoint for traces |
| `OTEL_LOGS_URL` | No | `http://localhost:4318/v1/logs` | OTLP HTTP endpoint for logs |
