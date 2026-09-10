package com.akl.bctemplate.domain.notes

import java.time.Instant
import java.util.UUID

// Facts, not commands: named in the past tense, and once appended, never changed or removed.
// A plain, bounded-context-local hierarchy — NoteEventCodec is what maps these to/from the
// generic DomainEvent envelope EventStore actually persists (see its doc for why).
sealed interface NoteEvent {
    val noteId: UUID
    val occurredAt: Instant
}

data class NoteCreated(
    override val noteId: UUID,
    val title: String,
    val body: String,
    override val occurredAt: Instant,
) : NoteEvent

data class NotePublished(
    override val noteId: UUID,
    override val occurredAt: Instant,
) : NoteEvent

data class NoteArchived(
    override val noteId: UUID,
    override val occurredAt: Instant,
) : NoteEvent
