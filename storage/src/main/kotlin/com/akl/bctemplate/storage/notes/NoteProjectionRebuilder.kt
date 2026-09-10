package com.akl.bctemplate.storage.notes

import com.akl.bctemplate.domain.notes.Note
import com.akl.bctemplate.domain.notes.NoteEvent
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

// Proof that notes_projection is disposable, derived state: wipe it and replay every event
// from notes_events, in order, back into existence — the same Note.apply() fold
// ExposedNoteEventStore uses to keep it live, just run once over the whole log instead of one
// event at a time. A real system would call this from an admin action or when introducing a
// new projection, not automatically on every startup.
@Component
@Transactional
class NoteProjectionRebuilder {

    fun rebuildAll() {
        NotesProjectionTable.deleteAll()
        eventsByAggregateInOrder().forEach { (_, events) ->
            var note: Note? = null
            events.forEachIndexed { index, event ->
                note = Note.apply(note, event)
                upsertProjection(note, version = index + 1L)
            }
        }
    }

    private fun eventsByAggregateInOrder(): Map<UUID, List<NoteEvent>> =
        NoteEventsTable.selectAll()
            .orderBy(NoteEventsTable.aggregateId to SortOrder.ASC, NoteEventsTable.version to SortOrder.ASC)
            .map {
                NoteEventCodec.decode(
                    eventType = it[NoteEventsTable.eventType],
                    aggregateId = it[NoteEventsTable.aggregateId],
                    occurredAt = it[NoteEventsTable.occurredAt].toInstant(),
                    payload = it[NoteEventsTable.payload],
                )
            }
            .groupBy { it.noteId }
}
