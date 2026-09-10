package com.akl.bctemplate.domain.notes

import java.time.Instant
import java.util.UUID

// The aggregate is a pure fold of its event history, never persisted directly — see
// NoteEventStore. Command methods (publish/archive) don't mutate; they inspect the current
// state and return the next event to append, or null if the transition is invalid.
data class Note(
    val id: UUID,
    val title: String,
    val body: String,
    val status: NoteStatus,
    val createdAt: Instant,
    val publishedAt: Instant? = null,
) {
    fun publish(now: Instant = Instant.now()): NotePublished? {
        if (status != NoteStatus.DRAFT) return null
        if (title.isBlank() || body.isBlank()) return null
        return NotePublished(id, now)
    }

    fun archive(now: Instant = Instant.now()): NoteArchived? {
        if (status == NoteStatus.ARCHIVED) return null
        return NoteArchived(id, now)
    }

    companion object {
        // The single place that defines what each event means. Reused on the write side to
        // rebuild the aggregate before deciding the next command (see NoteService), and on the
        // read side by storage to fold events into the notes_projection table (see
        // ExposedNoteEventStore and NoteProjectionRebuilder) — one fold, two consumers.
        fun apply(note: Note?, event: NoteEvent): Note = when (event) {
            is NoteCreated -> Note(
                id = event.noteId,
                title = event.title,
                body = event.body,
                status = NoteStatus.DRAFT,
                createdAt = event.occurredAt,
            )

            is NotePublished -> checkNotNull(note) { "NotePublished for a note with no prior NoteCreated event" }
                .copy(status = NoteStatus.PUBLISHED, publishedAt = event.occurredAt)

            is NoteArchived -> checkNotNull(note) { "NoteArchived for a note with no prior NoteCreated event" }
                .copy(status = NoteStatus.ARCHIVED)
        }

        fun replay(events: List<NoteEvent>): Note? = events.fold(null as Note?) { note, event -> apply(note, event) }
    }
}
