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
2. **`domain` defines ports as plain interfaces; `storage` implements them as adapters.** Every bounded context in this template is event-sourced (see "Event Sourcing" below), so that's typically *two* ports: an event store (e.g. `NoteEventStore`, the source of truth) and a read-only projection repository (e.g. `NoteRepository`). Their Exposed-backed implementations (`ExposedNoteEventStore`, `ExposedNoteRepository`) live in `storage`. `domain` never imports anything from `storage`.
3. **Business logic lives in `domain`, not in controllers, schedulers, or repository adapters.** A controller's job is to translate HTTP ↔ domain calls; a repository adapter's job is to translate domain calls ↔ SQL. Branching business rules (state transitions, validation, calculations) belong in a `@DomainService` or in behavior methods on the domain entity itself that return the next event rather than mutating state (see `Note.publish()`/`Note.archive()`).
4. **Application-layer classes never inject `Repository` interfaces directly.** Controllers and schedulers depend on `@DomainService` classes only; a `@DomainService` is the sole caller of the event store and projection ports. This keeps every transaction/consistency rule in one place per bounded context instead of scattered across controllers.
5. **Domain services are framework-free and wired automatically.** A `@DomainService`-annotated class carries no Spring annotations itself. `application`'s `DomainConfiguration` uses `@ComponentScan(basePackages = ["com.akl.bctemplate.domain"], includeFilters = [ComponentScan.Filter(ANNOTATION, DomainService::class)])` to register every `@DomainService` class as a Spring bean automatically — adding a new bounded context's service requires **no edit to any wiring file**, just the `@DomainService` annotation on the new class. `domain/build.gradle.kts`'s `kotlin.plugin.allopen { annotation(...DomainService) }` makes these classes non-final so Spring (and the tracing aspect below) can proxy them.
6. **Cross-cutting concerns hook in via the `@DomainService` marker, not by touching domain code.** `DomainServiceTracingAspect` wraps every `@DomainService` method in an OpenTelemetry span via AspectJ (`@Around("@within(com.akl.bctemplate.domain.services.DomainService)")`) — this is how tracing is added without a single `import io.opentelemetry` appearing anywhere in `domain`.
7. **Each bounded context is a top-level package, mirrored across all three modules.** `domain/.../notes`, `storage/.../notes`, `application/.../notes` — a bounded context's code is easy to find and easy to delete as a unit. Bounded contexts do not import each other's internals; if two need to collaborate, that's a `@DomainService` in one calling a public port/service of the other, decided deliberately rather than by convenience.
8. **The event-sourced write side stays invisible above the domain/storage seam.** `NoteService`'s public methods (`createNote`, `publishNote`, ...) have the same shapes an ordinary CRUD service's would; `application`'s controllers, `DomainConfiguration`, and everything else outside `domain`/`storage` needed zero changes when `notes` was rewritten from a plain `save()`-based repository to an event store + projection. That's the payoff of the port boundary — the persistence strategy behind it is free to change.

## domain

The core of the hexagon. No external dependencies — only Kotlin stdlib and test libraries.

```
domain/src/main/kotlin/com/akl/bctemplate/domain/
├── services/DomainService.kt   # marker annotation
├── Pagination.kt                # Page<T> / PageRequest — a small generic building block, not tied to any one bounded context
├── Tracer.kt                    # Tracer / SpanScope ports — backs the tracing aspect below without a framework import
└── notes/                       # example bounded context — see "Using This Template"
```

## storage

Infrastructure adapter. Implements the event store and projection ports defined in `domain`.

```
storage/src/main/
├── kotlin/com/akl/bctemplate/storage/   # Exposed ORM: an event store + a projection repository per bounded context
└── resources/db/migration/               # Flyway SQL migrations (PostgreSQL) — an events table + a projection table per bounded context
```

Technologies: Jetbrains Exposed ORM, Flyway, PostgreSQL.

## Event Sourcing

Every bounded context's write side is event-sourced — `notes` is the worked example, read this section alongside its files.

- **The aggregate is a fold, not a row.** `Note` is reconstructed by replaying its event history (`Note.replay(events)`), not loaded as a row of current-state columns. Command methods (`publish()`, `archive()`) don't mutate the aggregate; they inspect it and return the next event to append, or `null` if the transition is invalid. `Note.apply(note, event)` is the single place that defines what each event means, and it's reused by both sides below — one fold, two consumers.
- **Two ports, two tables, one bounded context.** `NoteEventStore` (`append`/`loadEvents`) is the source of truth, backed by an append-only `notes_events` table: a global `id` (append order across every aggregate), `(aggregate_id, version)` (the per-Note sequence, unique — see concurrency below), `event_type`, `payload`, `occurred_at`. `NoteRepository` (`findById`/`findAll`, no `save()`) is a read-only query port over `notes_projection`, a denormalized table shaped like the read model, plus a `version` column recording the last event folded into it.
- **The projection is kept live synchronously, as a storage-only concern.** `ExposedNoteEventStore.append()` writes the new event rows *and*, in the same transaction, folds each one via `Note.apply()` into an upsert on `notes_projection` — no queue, no outbox, no async worker. Nothing above `domain`/`storage` knows this happens; a system that can't afford synchronous updates could swap in an outbox-publishing `NoteEventStore` behind the same port without touching `NoteService` or `application` at all.
- **The projection is provably disposable.** `NoteProjectionRebuilder.rebuildAll()` truncates `notes_projection` and replays the entire `notes_events` log back into it, aggregate by aggregate, using the exact same `Note.apply()` fold. If that produces the same rows the live projection already had (see `NoteEventStoreIntegrationTest`), the projection was never carrying information the event log didn't already have.
- **Optimistic concurrency.** `append(aggregateId, expectedVersion, events)` takes the version the caller last saw (0 for a brand-new aggregate); a mismatch throws `ConcurrentEventAppendException` — two commands raced on the same Note. The `UNIQUE (aggregate_id, version)` index on `notes_events` is the real safety net against a race between the check and the insert; the exception is a friendlier fast-path for the common case.
- **Payloads cross a (de)serialization boundary in storage, not domain.** `NoteEventCodec` turns each `NoteEvent` subtype into an `(event_type, payload)` pair and back — `domain` never imports a JSON library. Events with no extra fields (`NotePublished`, `NoteArchived`) still get a payload (`{}`), keeping every row's shape uniform.

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

**Data isolation without restarting:**

- Use randomly generated IDs (e.g. `UUID.randomUUID()`) for every entity created in a test. The container runs with `withReuse(true)`, so a persistent database is shared across test runs; random IDs prevent collisions.
- Clean up test data only when necessary (e.g. a test asserts on an exact row count). Prefer random IDs so leftover data from previous runs is invisible to other tests.

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

1. **domain**: define the events (a sealed `XEvent` interface + one data class per fact, past tense, immutable), then the aggregate (a fold: `X.apply(x, event)` + `X.replay(events)`, plus command methods that return the next event or `null`) — write failing unit tests for both first. Define `XEventStore` (`append`/`loadEvents`) and `XRepository` (`findById`/`findAll`, read-only) as plain interfaces. Write a failing unit test for the `@DomainService` using one fake class that implements *both* ports and applies the same fold internally (see `NoteServiceTest`'s `FakeNoteEventStore` — this is what keeps the fake honest about how the real adapter behaves), then the service.
2. **storage**: define an events table (`x_events`: `id` bigserial, `aggregate_id` uuid, `version` bigint, `event_type` varchar, `payload` text/jsonb, `occurred_at` timestamptz, `UNIQUE (aggregate_id, version)`) and a projection table (`x_projection`: the read model's columns plus a `version` bigint) in a Flyway migration under `storage/src/main/resources/db/migration/V<next>__<description>.sql`. Implement `ExposedXEventStore : XEventStore` (append events, then fold each one via `X.apply()` to upsert the projection row, in the same transaction) and `ExposedXRepository : XRepository` (read-only queries against the projection table) — see `ExposedNoteEventStore`/`ExposedNoteRepository`/`NoteEventCodec`/`NoteProjection.kt` for the full worked example, including a `ProjectionRebuilder` (optional but recommended). No config wiring needed — `@Repository`/`@Component` are picked up by Spring Boot's own component scan (`scanBasePackages` already covers `storage`), and the `@DomainService` is picked up by `DomainConfiguration`'s `@ComponentScan` (see Hexagonal Architecture Rules, rule 5).
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
