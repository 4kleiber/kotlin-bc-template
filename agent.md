# Architecture

This template uses **hexagonal architecture** (a.k.a. ports and adapters) with three Gradle modules:

```
application → {domain, storage}
storage     → {domain}
domain      → {}
```

The arrows are dependency directions, and they are the whole point of the template: `domain` depends on nothing, `storage` depends only on `domain`, and `application` depends on both. Dependencies never point the other way.

## Hexagonal Architecture Rules

These are hard rules, not stylistic preferences — they're what makes a bounded context testable in isolation and swappable at its edges:

1. **`domain` has zero framework dependencies.** No Spring, no Exposed, no Jackson, no JPA — only the Kotlin stdlib and test libraries (JUnit5, AssertJ). If a class in `domain` needs an `import` from outside `kotlin.*`/`java.*`/a test library, that's a signal the logic belongs in `storage` or `application` instead.
2. **`domain` defines ports as plain interfaces; `storage` implements them as adapters.** Every bounded context in this template is event-sourced (see "Event Sourcing" below) against one shared, generic `EventStore` port — there is no per-bounded-context port (no "NoteEventStore"). `storage`'s `ExposedEventStore` is the only implementation, used by every `@DomainService`. `domain` never imports anything from `storage`.
   A bounded context's own events (e.g. `NoteEvent`) are a plain sealed hierarchy, unrelated to any storage type; its own codec (e.g. `NoteEventCodec`) maps them to/from the generic `DomainEvent` envelope `EventStore` persists — see "Event Sourcing".
3. **Business logic lives in `domain`, not in controllers, schedulers, or storage adapters.** A controller's job is to translate HTTP ↔ domain calls; `ExposedEventStore`'s job is to translate the generic event shape ↔ SQL — it doesn't know what a "Note" is. Branching business rules (state transitions, validation, calculations) belong in a `@DomainService` or in behavior methods on the domain entity itself that return the next event rather than mutating state (see `Note.publish()`/`Note.archive()`).
4. **Application-layer classes never inject `EventStore` directly.** Controllers and schedulers depend on `@DomainService` classes only; a `@DomainService` is the sole caller of `EventStore`. This keeps every transaction/consistency rule, and every translation between typed events and the generic envelope, in one place per bounded context instead of scattered across controllers.
5. **Domain services are framework-free and wired automatically.** A `@DomainService`-annotated class carries no Spring annotations itself. `application`'s `DomainConfiguration` uses `@ComponentScan(basePackages = ["com.akl.bctemplate.domain"], includeFilters = [ComponentScan.Filter(ANNOTATION, DomainService::class)])` to register every `@DomainService` class as a Spring bean automatically — adding a new bounded context's service requires **no edit to any wiring file**, just the `@DomainService` annotation on the new class. `domain/build.gradle.kts`'s `kotlin.plugin.allopen { annotation(...DomainService) }` makes these classes non-final so Spring (and the tracing aspect below) can proxy them.
6. **Cross-cutting concerns hook in via the `@DomainService` marker, not by touching domain code.** `DomainServiceTracingAspect` wraps every `@DomainService` method in an OpenTelemetry span via AspectJ (`@Around("@within(com.akl.bctemplate.domain.services.DomainService)")`) — this is how tracing is added without a single `import io.opentelemetry` appearing anywhere in `domain`.
7. **Each bounded context is a top-level package, mirrored across all three modules.** `domain/.../notes`, `storage/.../notes`, `application/.../notes` — a bounded context's code is easy to find and easy to delete as a unit. Bounded contexts do not import each other's internals; if two need to collaborate, that's a `@DomainService` in one calling a public port/service of the other, decided deliberately rather than by convenience.
8. **The event-sourced write side stays invisible above the domain/storage seam.** `NoteService`'s public methods (`createNote`, `publishNote`, ...) have the same shapes an ordinary CRUD service's would; `application`'s controllers, `DomainConfiguration`, and everything else outside `domain`/`storage` needed zero changes across two full storage rewrites (a plain `save()`-based repository → a per-context event-sourced table + projection → today's shared, on-the-fly `EventStore`). That's the payoff of the port boundary — the persistence strategy behind it is free to change.

## domain

The core of the hexagon. No external dependencies — only Kotlin stdlib and test libraries.

```
domain/src/main/kotlin/com/akl/bctemplate/domain/
├── services/DomainService.kt   # marker annotation
├── DomainEvent.kt                # the generic, stored-event envelope every bounded context's codec maps to/from
├── EventStore.kt                 # the ONE event-sourcing port — no per-bounded-context event store
├── ConcurrentEventAppendException.kt
├── Pagination.kt                # Page<T> / PageRequest — used by EventStore.listStreamIds and any bounded context's own paging
├── Tracer.kt                    # Tracer / SpanScope ports — backs the tracing aspect below without a framework import
└── notes/                       # example bounded context — see "Using This Template"
```

## storage

Infrastructure adapter. Implements `EventStore` — the one port every bounded context's `@DomainService` calls.

```
storage/src/main/
├── kotlin/com/akl/bctemplate/storage/
│   └── eventstore/
│       ├── EventsTable.kt       # the ONE events table every bounded context appends to
│       └── ExposedEventStore.kt # the ONE EventStore implementation — generic, not "Note"-specific
└── resources/db/migration/       # a single Flyway migration creates the events table; bounded contexts never add their own
```

There is no `storage/notes/` package: the `notes` bounded context has zero storage-layer code of its own — see "Event Sourcing".

Technologies: Jetbrains Exposed ORM, Flyway, PostgreSQL.

## Event Sourcing

Every bounded context's write side is event-sourced against one shared `EventStore` — `notes` is the worked example, read this section alongside its files.

- **The aggregate is a fold, not a row — and neither is anything else.** `Note` is reconstructed by replaying its event history (`Note.replay(events)`), not loaded as a row of current-state columns, and this happens on **every read**, not just before deciding a command: `NoteService.findNote()`/`listNotes()` call `EventStore.loadEvents()` + `Note.replay()` fresh each time. There is no persisted, kept-in-sync read model (no "projection table") anywhere in this template — deliberately: one fewer thing that can drift out of sync with the event log, at the cost of replaying a stream on every read. If that cost ever matters for a real stream, the standard fix is a *snapshot* (the folded state at a known version, so replay resumes from there instead of from scratch) — not implemented here on purpose; add one if and when a bounded context actually needs it, per `EventStore`'s doc comment.
- **One `EventStore`, shared by every bounded context — no "NoteEventStore".** `EventStore` (`append`/`loadEvents`/`listStreamIds`) is the single port every `@DomainService` calls directly; `ExposedEventStore` is its one implementation. Neither knows what a "Note" is — `streamType` is just a string a bounded context's codec supplies (`NoteEventCodec.STREAM_TYPE = "Note"`). Adding a second bounded context adds zero new storage classes: it reuses the same `EventStore` bean with its own `streamType`.
- **`DomainEvent` is a generic envelope, not a base class business events extend.** It mirrors the `events` table's columns exactly: `id`, `tenantId`, `streamId`, `streamType`, `version`, `eventType`, `eventData: Map<String, Any?>`, `metadata: Map<String, Any?>?`, `createdAt`, `sequenceNumber`. A bounded context's own events (`NoteEvent` — `NoteCreated`/`NotePublished`/`NoteArchived`) are a plain, unrelated sealed hierarchy; its own codec (`NoteEventCodec`) maps between the two, encoding/decoding `eventType`/`eventData` — pure Kotlin `Map` manipulation, so neither `domain` file needs a JSON library. Only `storage.eventstore.EventsTable` turns `eventData`/`metadata` into actual JSONB text.
- **One events table, shared by every bounded context and every tenant, in the exact order events were created.** `stream_type` + `stream_id` identify one aggregate's stream (`ExposedEventStore` always filters by both, plus `tenant_id`); `version` is that stream's own sequence (unique together with `tenant_id`/`stream_id` — see concurrency below), replayed in order by `loadEvents()`. `id` defaults to Postgres 18's native `uuidv7()` — roughly time-ordered, but not a strict guarantee. `sequence_number` is a separate, purely-additive `BIGSERIAL` column that *is* a strict guarantee: one global counter spanning every tenant, stream, and bounded context, giving the exact order every event was ever created in — which is what "the exact order of event creation" means once everything shares one sequence instead of each stream (or each bounded context) getting its own independent one.
- **`listStreamIds` is how reads find streams without a projection to list them.** With no persisted read model, `NoteService.listNotes()` can't just query "all Notes" from a table — it asks `EventStore.listStreamIds(tenantId, "Note", pageRequest)` for the distinct `stream_id`s of that type, then replays each one. `ExposedEventStore`'s implementation is a plain distinct-and-paginate-in-memory query, documented as the first thing to revisit if a stream type ever gets large.
- **Optimistic concurrency.** `append(tenantId, streamId, streamType, expectedVersion, events)` takes the version the caller last saw (0 for a brand-new stream); a mismatch throws `ConcurrentEventAppendException` — two commands raced on the same stream. The `UNIQUE (tenant_id, stream_id, version)` constraint on `events` is the real safety net against a race between the check and the insert; the exception is a friendlier fast-path for the common case.
- **Multi-tenancy exists in the store even though `notes` doesn't use it.** Every row carries a `tenant_id`, and `EventStore`'s methods all take one — this template's `NoteService` just always passes the same well-known constant (see its doc comment), since `notes` isn't itself a tenant-aware bounded context. A real tenant-aware context would thread an actual tenant id through from whatever identifies the caller instead.

## application

Spring Boot entry point, HTTP adapters, and server-side frontend. Depends on both `domain` and `storage`.

```
application/src/main/
├── kotlin/com/akl/bctemplate/application/
│   ├── BcTemplateApplication.kt          # Spring Boot main
│   └── configuration/DomainConfiguration.kt  # @ComponentScan wiring — see rule 5 above
└── resources/
    ├── application.yaml                   # base config
    └── application-otel.yaml              # opt-in OpenTelemetry OTLP export profile
```

Technologies: Spring Boot MVC, kotlinx.html (frontend), OpenTelemetry (OTLP export via the `otel` profile).

# Tech Stack

- **Backend**: Spring Boot with Kotlin
- **Database**: Jetbrains Exposed (DSL API) for all database queries
- **Frontend**: kotlinx.html with Bootstrap; UI components are implemented as extension functions on `FlowContent` (or the appropriate kotlinx.html receiver). Not every bounded context needs a UI page — the `notes` example includes one minimal page to demonstrate the convention; a REST controller alone is a valid and common choice for an API-only bounded context.

# Configuration

All Spring profiles and environment variables are documented in `docs/configuration.md`.

**Rule**: whenever you add, rename, or remove a Spring profile, a `${ENV_VAR}` placeholder in
any `application*.yaml` file, keep `docs/configuration.md` in sync in the same commit.

# Documentation

Document essential decisions, constraints, and non-obvious behavior — either in code or in dedicated `.md` files. Skip documentation that restates what the code already clearly expresses.

Document in code (inline comment) when:
- A constraint or invariant isn't obvious from the types or names
- A workaround exists for a specific external limitation
- Behavior would surprise a reader without context

Document in a `.md` file when:
- The scope spans multiple files or systems (e.g. architectural decisions, API contracts, runbook steps)
- The audience is broader than the immediate code reader (e.g. onboarding, ops)

# Test Driven Development

Follow a strict TDD process for all changes:

1. **Red** — Write a failing test that defines the desired behavior before writing any implementation code.
2. **Green** — Write the minimal implementation to make the test pass.
3. **Refactor** — Clean up the code while keeping all tests green.

Rules:
- Never write production code without a failing test that demands it.
- Each cycle should be small — one behavior per test.
- Run the full test suite after each green/refactor step to ensure nothing is broken.
- Test all method paths like if/else should produce 2 tests.

## Bug Fixing with TDD

When fixing a bug, always use the test-first approach:

1. **Reproduce** — Write a test that exposes the bug. The test must fail, proving the bug exists.
2. **Fix** — Write the minimal code change to make the test pass.
3. **Verify** — Run the full test suite to confirm the fix doesn't introduce regressions.

Rules:
- Never fix a bug without a failing test that reproduces it first.
- The reproducing test serves as a regression guard — it ensures the bug cannot silently return.
- If the bug is hard to isolate, narrow it down with multiple small tests targeting different aspects of the behavior.

## Test Quality

Tests must exercise real code, not just assert trivial truths:

- Every test must invoke production code and assert on its output or side effects.
- Never write tautological tests (e.g. `assertEquals(1, 1)`) — a test that can't fail is worthless.
- The test should break if the behavior it covers is changed or removed. If it can't, it's not testing anything.
- Assert on values produced by the system under test, not on hardcoded constants mirroring the implementation.

## Integration Tests

All integration tests extend `AbstractIntegrationTest` (application module). It starts the Spring context once and keeps it alive for the entire suite.

**Rules to preserve a single shared context:**

- Do not add any annotation to an individual test class that changes the context configuration — this forces Spring to restart. Forbidden per-class annotations include:
  - `@MockBean` / `@SpyBean` — define them in `AbstractIntegrationTest` instead
  - `@TestPropertySource` — add properties to `application/src/test/resources/application.yaml`
  - `@ActiveProfiles` — already set to `"test"` in the base class; do not override
  - `@DirtiesContext` — never use this
- If a spy or mock is needed in only some tests, add it to the base class anyway; a dormant mock does not affect other tests but a context restart affects all of them.

**Data isolation without restarting — mandatory, not a suggestion:**

- **Every entity, tenant, and stream a test creates must use a fresh random identifier** — `UUID.randomUUID()` for UUIDs, an equivalent for any other ID type. Never a fixed, hardcoded, or sequential value (`UUID.fromString("...")` with a literal, `"test-user-1"`, incrementing counters). This applies everywhere an ID is test-generated: entity/aggregate/stream ids, tenant ids (`EventStoreIntegrationTest` generates its own `tenantId` per test for exactly this reason), usernames, emails — anything that could collide with another test's data.
- **Why this is mandatory, not just tidy**: `AbstractIntegrationTest`'s Postgres container runs with `withReuse(true)` and Ryuk disabled (see `testcontainers.properties`), so the *same* container — and every row any test has ever written to it — persists across test runs on a given machine, not just within one run. That reuse is what makes the local dev loop fast (skipping the ~1-2s container-start cost on every single `./gradlew test`); the trade-off is that stale data from a previous run is still sitting there. Random IDs are the only reason that's safe: a test can never collide with, overwrite, or accidentally match data from any earlier run. Falling back to a fresh container per run (or truncating tables between runs) to work around non-random IDs would defeat the entire point of `withReuse(true)` — don't do that; fix the IDs instead.
- Never clean up test data as a substitute for random IDs — write it once with a random identifier and leave it. Only delete data when a test's assertion genuinely requires it (e.g. asserting an exact row/stream count), and even then scope the deletion to that test's own random ID, never a table-wide truncate that would affect other tests' leftover data or a concurrently-running test.
- Never assert on a table-wide count, "first N rows/streams", or anything else that stale data from earlier runs could affect — assert only on entities identified by the test's own random IDs (see `EventStoreIntegrationTest`'s `listStreamIds` tests: each generates its own `tenantId`, so it only ever sees streams it created itself).

**Test environment parity with production:**

- `AbstractIntegrationTest` appends `currentSchema=bctemplate` to the Testcontainers JDBC URL, exactly matching the production datasource. This sets `search_path=bctemplate` for every connection — the same constraint the app runs under in production.
- Flyway is configured in `application/src/test/resources/application.yaml` with `spring.flyway.schemas: bctemplate` and `create-schemas: true` so it can bootstrap the `bctemplate` schema on a fresh Testcontainers database.

**Docker daemon (sandboxed/remote sessions):**

- Integration tests use Testcontainers, which requires a running Docker daemon. In a sandboxed remote session, `.claude/hooks/session-start.sh` (registered as a `SessionStart` hook) normally starts `dockerd` automatically when `CLAUDE_CODE_REMOTE=true`, polling for up to 30s.
- If `docker info` still fails (the hook didn't fire, or Docker stopped afterwards), `service docker start` / the `/etc/init.d/docker` script will also fail in this sandbox — it calls `ulimit -Hn 524288`, which the sandbox blocks (`ulimit: error setting limit (Operation not permitted)`).
- Manual fallback: start the daemon directly, bypassing the init script:
  ```sh
  nohup dockerd > /var/log/docker.log 2>&1 & disown
  docker info   # verify it came up
  ```

# Using This Template

## Starting a new service from this template

1. Create the new repo from this one (GitHub "Use this template", or `gh repo create <new-name> --template 4kleiber/kotlin-bc-template`).
2. Rename the project, in this order:
   - `settings.gradle.kts`: `rootProject.name`
   - Base package `com.akl.bctemplate` → your package, in every module (move the directory trees under `src/main/kotlin` and `src/test/kotlin`, update every `package`/`import` line)
   - `domain/build.gradle.kts`: the `allOpen { annotation(...) }` fully-qualified name
   - `application/.../BcTemplateApplication.kt`: class name and `scanBasePackages`
   - `application/build.gradle.kts`: Jib's `to.image` (defaults to `ghcr.io/4kleiber/kotlin-bc-template`; the release workflow overrides this at CI time via `-Djib.to.image=ghcr.io/${{ github.repository }}`, but the local default should still match your new repo)
   - Schema name `bctemplate` → your schema, everywhere it's referenced: `AbstractIntegrationTest`'s `currentSchema=...`, both `application.yaml` files' `spring.flyway.schemas`, and any Flyway migration comments that mention it
   - `.release-please-manifest.json`: reset to `{ ".": "0.1.0" }` (or `"0.0.0"` for a truly empty start) for the new project's own release history
   - `otel-collector-config.yml` / `docker-compose.yml`: rename the `otlp_http/bctemplate` exporter key to match, if kept
3. Decide what to do with the `notes` example: keep it briefly as a live reference for the pattern, then delete its three per-module packages (`domain/.../notes`, `storage/.../notes`, `application/.../notes`) plus its Flyway migration once your first real bounded context replaces it as the worked example.

## Adding a new bounded context to a service built from this template

Follow the `notes` example end-to-end, one TDD cycle at a time:

1. **domain**: define the events — a plain sealed `XEvent` interface, plus one data class per fact (past tense, immutable) — then the aggregate (a fold: `X.apply(x, event)` + `X.replay(events)`, plus command methods that return the next event or `null`) — write failing unit tests for both first. Define `XEventCodec` (`const val STREAM_TYPE`, `encode(event): NewDomainEvent`, `decode(stored: DomainEvent): XEvent`) mapping between `XEvent` and the generic envelope — pure `Map<String, Any?>` manipulation, no JSON library (see `NoteEventCodec`). Write the `@DomainService` directly against the shared `EventStore` port (constructor: `class XService(private val eventStore: EventStore)`) — no new port to define. Write a failing unit test for it first using one fake `EventStore` (see `NoteServiceTest`'s `FakeEventStore` — the same fake works for any bounded context, since `EventStore` is generic), then the service.
2. **storage**: nothing to do. The bounded context reuses the existing `ExposedEventStore` bean as-is — no new table, no new adapter class, no migration.
3. **application**: add a controller (REST, a kotlinx.html page, or both) under a new top-level package named after the bounded context. Write a failing integration test extending `AbstractIntegrationTest` first, then the controller to pass it — it only needs to know the `@DomainService`'s method signatures, not that they're event-sourced underneath (rule 8).
4. Update `docs/configuration.md` if the new bounded context introduces a profile or `${ENV_VAR}` placeholder (see the Configuration rule above).
5. Run `./gradlew test` — the whole suite, not just the new tests — before committing.

# Conventional Commits

All commit messages must follow the [Conventional Commits](https://www.conventionalcommits.org/) specification.

Format: `<type>(<optional scope>): <description>`

**Types:**
- `feat` — a new feature (triggers a minor release)
- `fix` — a bug fix (triggers a patch release)
- `chore` — maintenance tasks, dependency updates, tooling (no release)
- `docs` — documentation only changes (no release)
- `refactor` — code change that neither fixes a bug nor adds a feature (no release)
- `test` — adding or correcting tests (no release)
- `perf` — a code change that improves performance (triggers a patch release)
- `ci` — changes to CI/CD configuration (no release)
- `build` — changes to the build system or external dependencies (no release)

**Breaking changes**: append `!` after the type/scope (e.g. `feat!:`) or add `BREAKING CHANGE:` in the footer. Triggers a major release.

**Examples:**
```
feat(notes): add publish/archive lifecycle to Note
fix(storage): prevent duplicate key on concurrent insert
chore: upgrade Kotlin to 2.5.0
docs: document OTLP profile activation
refactor(domain): extract note validation into value object
```

**Enforcement**: Every commit on this repository must use a conventional commit message. Agents must never create a commit without a valid conventional commit prefix.

The repository ships a `commit-msg` hook at `.githooks/commit-msg` that rejects non-conforming messages. Activate it in every new checkout with:

```sh
git config core.hooksPath .githooks
```
