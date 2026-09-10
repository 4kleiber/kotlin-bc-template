package com.akl.bctemplate.storage.notes

import com.akl.bctemplate.domain.notes.Note
import com.akl.bctemplate.domain.notes.NoteEvent
import com.akl.bctemplate.storage.eventstore.EventsTable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

// Proof that notes_projection is disposable, derived state: wipe it and replay every "Note"
// event from the shared events table (aggregate_type = NoteEvent.AGGREGATE_TYPE), in order,
// back into existence — the same Note.apply() fold ExposedNoteEventStore uses to keep it
// live, just run once over the whole log instead of one event at a time. A real system would
// call this from an admin action or when introducing a new projection, not automatically on
// every startup. Filtering by aggregate_type matters here specifically: without it, a second
// bounded context's rows sharing this table would fail NoteEventCodec.decode() with an
// "Unknown note event type" error.
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
        EventsTable.selectAll()
            .where { EventsTable.aggregateType eq NoteEvent.AGGREGATE_TYPE }
            .orderBy(EventsTable.aggregateId to SortOrder.ASC, EventsTable.version to SortOrder.ASC)
            .map {
                NoteEventCodec.decode(
                    eventType = it[EventsTable.eventType],
                    aggregateId = it[EventsTable.aggregateId],
                    occurredAt = it[EventsTable.occurredAt].toInstant(),
                    payload = it[EventsTable.payload],
                )
            }
            .groupBy { it.noteId }
}
