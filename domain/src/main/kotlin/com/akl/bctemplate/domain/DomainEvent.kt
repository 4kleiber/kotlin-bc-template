package com.akl.bctemplate.domain

import java.time.Instant
import java.util.UUID

// Mirrors the shared `events` table's columns exactly (see storage's EventsTable) — this is
// what a stored, already-appended event looks like once read back from EventStore.loadEvents.
// `eventData`/`metadata` are generic key-value payloads rather than a typed business event:
// each bounded context's own codec (e.g. notes' NoteEventCodec) maps them to/from its own
// typed events based on `eventType`. Keeping them as a plain Kotlin Map — not a JSON string —
// is what lets domain stay framework-free while storage still owns turning them into/out of
// JSONB; only storage imports a JSON library.
data class DomainEvent(
    val id: UUID,
    val tenantId: UUID,
    val streamId: UUID,
    val streamType: String,
    val version: Long,
    val eventType: String,
    val eventData: Map<String, Any?>,
    val metadata: Map<String, Any?>?,
    val createdAt: Instant,
    // A separate, purely-additive counter — see EventStore's doc — giving the exact global
    // order every event was ever created in, across every tenant and stream, not just the
    // order within one stream (that's `version`'s job).
    val sequenceNumber: Long,
)

// What a caller hands to EventStore.append() before it exists as a row: no id, version, or
// sequenceNumber yet — the store assigns those. `occurredAt` is the domain's own timestamp for
// when the fact happened (e.g. Note.publish()'s `now` parameter), persisted as `created_at`
// rather than left to whatever moment the INSERT physically runs.
data class NewDomainEvent(
    val eventType: String,
    val eventData: Map<String, Any?>,
    val metadata: Map<String, Any?>? = null,
    val occurredAt: Instant,
)
