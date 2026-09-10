package com.akl.bctemplate.domain

import java.time.Instant
import java.util.UUID

// What every bounded context's event needs before it's ever persisted. A bounded context's
// own event hierarchy (e.g. notes.NoteEvent/NoteCreated) extends this directly and overrides
// `streamId`/`occurredAt` in its own primary constructor — required for Kotlin's data class
// equals()/hashCode()/copy() to actually include them, since data classes only look at their
// own primary-constructor properties, not inherited ones — and implements `data()` with
// whatever extra business fields that event carries.
//
// `data()` is the one method EventStore needs to persist ANY bounded context's event
// generically: ExposedEventStore.append() calls `event.streamId`/`.eventType`/`.occurredAt`/
// `.metadata`/`.data()` and never imports a bounded-context-specific type. There's
// deliberately no encode/decode step on the way in — a NoteCreated instance already *is* a
// DomainEvent, handed straight to EventStore.append(). Reading back still needs one (see
// StoredEvent's doc) — that direction can't be avoided by any amount of inheritance, since
// turning a generic row back into a specific Kotlin type requires code that knows the type.
abstract class DomainEvent(
    open val streamId: UUID,
    open val streamType: String,
    open val eventType: String,
    open val occurredAt: Instant,
    open val metadata: Map<String, Any?>? = null,
) {
    abstract fun data(): Map<String, Any?>
}

// The generic materialization of an already-persisted row (see EventStore.loadEvents). `id`,
// `tenantId`, `version`, and `sequenceNumber` only exist once a row does, so they live here
// rather than on DomainEvent itself — a "new" event a bounded context constructs doesn't know
// them yet. A bounded context's own decoder (e.g. notes.NoteEventDecoder) pattern-matches on
// `eventType` + `data()` to reconstruct its own typed event for replay.
data class StoredEvent(
    override val streamId: UUID,
    override val streamType: String,
    override val eventType: String,
    override val occurredAt: Instant,
    override val metadata: Map<String, Any?>?,
    val id: UUID,
    val tenantId: UUID,
    val version: Long,
    val sequenceNumber: Long,
    val eventData: Map<String, Any?>,
) : DomainEvent(streamId, streamType, eventType, occurredAt, metadata) {
    override fun data(): Map<String, Any?> = eventData
}
