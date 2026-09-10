package com.akl.bctemplate.domain.notes

import java.time.Instant
import java.util.UUID

// Facts, not commands: named in the past tense, and once appended to the event store, never
// changed or removed. This sealed hierarchy is the only place `event_type`/`payload` in
// storage's notes_events table are given meaning — see Note.apply().
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
