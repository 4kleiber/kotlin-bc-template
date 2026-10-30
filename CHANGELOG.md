# Changelog

## [1.0.1](https://github.com/4kleiber/kotlin-bc-template/compare/v1.0.0...v1.0.1) (2026-10-30)


### Bug Fixes

* **deps:** update dependency io.opentelemetry.instrumentation:opentelemetry-instrumentation-annotations to v2.32.0 ([#12](https://github.com/4kleiber/kotlin-bc-template/issues/12)) ([d4e8a64](https://github.com/4kleiber/kotlin-bc-template/commit/d4e8a64637476f0eea7b6c202ca7e5dcf43ae9fe))
* **deps:** update dependency io.opentelemetry.instrumentation:opentelemetry-instrumentation-annotations to v2.33.0 ([#21](https://github.com/4kleiber/kotlin-bc-template/issues/21)) ([3759041](https://github.com/4kleiber/kotlin-bc-template/commit/37590417fdd7217fece8862425881e109c2bb2dc))
* **deps:** update dependency io.opentelemetry.instrumentation:opentelemetry-instrumentation-annotations to v2.33.1 ([#22](https://github.com/4kleiber/kotlin-bc-template/issues/22)) ([c5bfe34](https://github.com/4kleiber/kotlin-bc-template/commit/c5bfe340806e9da2de6a705650aca563a3cd51f2))
* **deps:** update dependency net.ttddyy.observation:datasource-micrometer-spring-boot to v2.3.0 ([#13](https://github.com/4kleiber/kotlin-bc-template/issues/13)) ([7f037e5](https://github.com/4kleiber/kotlin-bc-template/commit/7f037e52020f0554332295671c99c2a064df95d8))
* **deps:** update exposedversion to v1.5.1 ([#9](https://github.com/4kleiber/kotlin-bc-template/issues/9)) ([269b190](https://github.com/4kleiber/kotlin-bc-template/commit/269b190ce1181f87436ed5b8d62b0b3d61bc82f7))
* **deps:** update exposedversion to v1.6.0 ([#17](https://github.com/4kleiber/kotlin-bc-template/issues/17)) ([a638b1b](https://github.com/4kleiber/kotlin-bc-template/commit/a638b1bfe183e58566f220434a2b1a833bd8c11f))
* **deps:** update flyway to v13.10.0 ([#14](https://github.com/4kleiber/kotlin-bc-template/issues/14)) ([63cfa12](https://github.com/4kleiber/kotlin-bc-template/commit/63cfa12b82a26933a7417a4a466e146bb873786a))
* **deps:** update flyway to v13.10.3 ([#16](https://github.com/4kleiber/kotlin-bc-template/issues/16)) ([b590fe5](https://github.com/4kleiber/kotlin-bc-template/commit/b590fe55cb437614242daccb186c6cad5bc9b7f2))
* **deps:** update flyway to v13.11.1 ([#19](https://github.com/4kleiber/kotlin-bc-template/issues/19)) ([791591a](https://github.com/4kleiber/kotlin-bc-template/commit/791591a0f1b2f86a31aa6b547cc1617863fa0ff8))
* **deps:** update flyway to v13.12.0 ([#23](https://github.com/4kleiber/kotlin-bc-template/issues/23)) ([8262138](https://github.com/4kleiber/kotlin-bc-template/commit/82621380e01f3792cdf9b756fac85245d39e1cb6))

## [1.0.0](https://github.com/4kleiber/kotlin-bc-template/compare/v0.1.0...v1.0.0) (2026-09-11)


### ⚠ BREAKING CHANGES

* **storage:** the events table's schema changed (aggregate_type/ aggregate_id -> tenant_id/stream_id/stream_type, plus sequence_number); notes_projection is gone.
* **domain:** NoteRepository, NoteEventStore, and their storage adapters are gone. Persistence goes through the shared EventStore port only; there is no read-model port to implement.
* **storage:** the notes_events table is gone; event rows now live in the shared events table with an added aggregate_type column.
* **domain:** NoteRepository no longer has save(); a storage adapter must instead implement the new NoteEventStore port.

### Features

* **application:** wire notes bounded context with REST API, UI, and OTel ([d04f000](https://github.com/4kleiber/kotlin-bc-template/commit/d04f0005b59531b1379b5115a25618319cf97958))
* **domain:** add framework-free domain module with notes example ([0714a7a](https://github.com/4kleiber/kotlin-bc-template/commit/0714a7ad6eb9b8a5324bd4de8427e937058eec0a))
* **storage:** add event store and projection adapters for notes ([005fff5](https://github.com/4kleiber/kotlin-bc-template/commit/005fff510a88165e79db3779231a13f2a619e00d))
* **storage:** add Exposed adapter and migration for notes ([98093bc](https://github.com/4kleiber/kotlin-bc-template/commit/98093bc6b64625dfd7dd25c1fdb1fcf629db3525))


### Code Refactoring

* **domain:** generic EventStore, no persisted read model ([a752132](https://github.com/4kleiber/kotlin-bc-template/commit/a75213201b3a353fbda2bf240c40e689ee74503d))
* **domain:** rebuild the notes bounded context as event-sourced ([ea75751](https://github.com/4kleiber/kotlin-bc-template/commit/ea757511acbbd8815bc274efd57475fe10694637))
* **storage:** one generic ExposedEventStore, delete storage/notes entirely ([3e378ef](https://github.com/4kleiber/kotlin-bc-template/commit/3e378ef5a9ee0c508ed64cd055ce1c6572cdc54e))
* **storage:** replace per-context notes_events with a shared events table ([16f0a1f](https://github.com/4kleiber/kotlin-bc-template/commit/16f0a1f7e599d019a9e375390b3b7f6d353eea9d))
