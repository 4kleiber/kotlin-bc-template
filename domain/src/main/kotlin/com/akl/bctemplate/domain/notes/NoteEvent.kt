package com.akl.bctemplate.domain.notes

import com.akl.bctemplate.domain.DomainEvent
import java.time.Instant
import java.util.UUID

// Facts, not commands: named in the past tense, and once appended to the event store, never
// changed or removed. Extends the shared DomainEvent so every "Note" fact lands in the same
// system-wide events table as every other bounded context's facts (see storage's
// EventsTable) — `noteId` is just a domain-readable alias for the generic `aggregateId`
// storage cares about.
sealed class NoteEvent(aggregateId: UUID, occurredAt: Instant) :
    DomainEvent(aggregateType = AGGREGATE_TYPE, aggregateId = aggregateId, occurredAt = occurredAt) {

    val noteId: UUID get() = aggregateId

    companion object {
        const val AGGREGATE_TYPE = "Note"
    }
}

data class NoteCreated(
    override val aggregateId: UUID,
    val title: String,
    val body: String,
    override val occurredAt: Instant,
) : NoteEvent(aggregateId, occurredAt)

data class NotePublished(
    override val aggregateId: UUID,
    override val occurredAt: Instant,
) : NoteEvent(aggregateId, occurredAt)

data class NoteArchived(
    override val aggregateId: UUID,
    override val occurredAt: Instant,
) : NoteEvent(aggregateId, occurredAt)
