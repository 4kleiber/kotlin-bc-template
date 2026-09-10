# kotlin-bc-template

A template repository showing how to structure a **bounded context** service in Kotlin
with **hexagonal architecture** (a.k.a. ports and adapters), **event sourcing**, Spring Boot,
and Jetbrains Exposed. It's built as three Gradle modules — `domain`, `storage`,
`application` — with a small, complete example bounded context (`notes`) wired end-to-end
through all three, plus a PR test pipeline, release-please releases, and Jib-based image
publishing to GHCR.

See [`agent.md`](agent.md) (symlinked as `CLAUDE.md`) for the full architecture rules, tech
stack, and TDD/commit conventions this repo follows and expects new bounded contexts to
follow.

## What's here

```
application → {domain, storage}   Spring Boot entry point, HTTP + kotlinx.html adapters
storage     → {domain}            Exposed event store + projection adapters, Flyway migrations
domain      → {}                  Framework-free events/aggregates, ports, @DomainService classes
```

The `notes` bounded context (draft → published → archived) is implemented in all three
modules as the worked example, **event-sourced end to end**: a sealed `NoteEvent` hierarchy
and an aggregate (`Note`) that's a fold of its event history rather than a stored row, an
event-store port + a read-only projection port, an `@DomainService`, an append-only
`notes_events` Exposed table plus a derived, rebuildable `notes_projection` table, a REST API
(`/api/notes`), and a minimal kotlinx.html page (`/notes`). See `agent.md`'s **Event
Sourcing** section for how the pieces fit together.

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
This Template → Adding a new bounded context** section for the full walkthrough (events →
aggregate → event store + projection ports → `@DomainService` → unit tests → events/projection
tables → adapters → Flyway migration → controller → integration test). Because
`DomainConfiguration` auto-registers every `@DomainService` via `@ComponentScan`, wiring a new
bounded context's service never requires editing a shared configuration file — and because the
event-sourced write side stays behind the domain/storage seam, nothing in `application` needs
to know it exists.

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
