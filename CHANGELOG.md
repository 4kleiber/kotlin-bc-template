# Changelog

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
