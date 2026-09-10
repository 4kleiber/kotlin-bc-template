# kotlin-bc-template

A template repository showing how to structure a **bounded context** service in Kotlin
with **hexagonal architecture** (a.k.a. ports and adapters), Spring Boot, and Jetbrains
Exposed. It's built as three Gradle modules — `domain`, `storage`, `application` — with a
small, complete example bounded context (`notes`) wired end-to-end through all three, plus a
PR test pipeline, release-please releases, and Jib-based image publishing to GHCR.

See [`agent.md`](agent.md) (symlinked as `CLAUDE.md`) for the full architecture rules, tech
stack, and TDD/commit conventions this repo follows and expects new bounded contexts to
follow.

## What's here

```
application → {domain, storage}   Spring Boot entry point, HTTP + kotlinx.html adapters
storage     → {domain}            Exposed ORM repository implementations, Flyway migrations
domain      → {}                  Framework-free entities, repository ports, @DomainService classes
```

The `notes` bounded context (draft → published → archived) is implemented in all three
modules as the worked example: a domain entity with behavior methods, a repository port and
`@DomainService`, an Exposed table and adapter, a Flyway migration, a REST API
(`/api/notes`), and a minimal kotlinx.html page (`/notes`).

## Starting a new service from this template

1. Use GitHub's **"Use this template"** button, or:
   ```sh
   gh repo create <new-name> --template 4kleiber/kotlin-bc-template
   ```
2. Follow the rename checklist in `agent.md`'s **Using This Template → Starting a new
   service from this template** section (project name, base package, schema name, Jib image,
   release-please manifest, etc).
3. Keep the `notes` example around briefly as a live reference, then delete it once your
   first real bounded context replaces it.

## Adding a new bounded context

Follow the `notes` example end-to-end, one TDD cycle at a time — see `agent.md`'s **Using
This Template → Adding a new bounded context** section for the full walkthrough (entity →
repository port → `@DomainService` → unit tests → Exposed table → adapter → Flyway migration
→ controller → integration test). Because `DomainConfiguration` auto-registers every
`@DomainService` via `@ComponentScan`, wiring a new bounded context's service never requires
editing a shared configuration file.

## Running locally

```sh
git config core.hooksPath .githooks   # enable the Conventional Commits check, once per clone
docker compose up -d                  # Postgres + Grafana LGTM + an OTel Collector
./gradlew bootRun                     # add --args='--spring.profiles.active=otel' to export traces/metrics/logs
```

The app listens on `http://localhost:8080`. With the `otel` profile active, traces, metrics,
and logs show up in Grafana at `http://localhost:3001`.

## Testing

```sh
./gradlew test
```

Runs the full suite across all three modules, including Testcontainers-backed integration
tests (a real Postgres container is started automatically — see `docs/configuration.md` for
how its version is pinned). Requires a running Docker daemon.

## Release & publish

Commits on `main` must follow [Conventional Commits](https://www.conventionalcommits.org/)
(enforced by the `.githooks/commit-msg` hook). Merging to `main` lets
[release-please](https://github.com/googleapis/release-please) open or update a release PR;
merging that PR tags a release, which triggers `.github/workflows/release.yml`'s `docker`
job to build and push the image via Jib to `ghcr.io/<owner>/<repo>:<version>` and `:latest`.
