package com.akl.bctemplate.storage.notes

import com.akl.bctemplate.domain.notes.ConcurrentEventAppendException
import com.akl.bctemplate.domain.notes.Note
import com.akl.bctemplate.domain.notes.NoteEvent
import com.akl.bctemplate.domain.notes.NoteEventStore
import com.akl.bctemplate.storage.eventstore.EventsTable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.ZoneOffset
import java.util.UUID

// The only class that ever writes "Note" facts into the shared events table (see
// EventsTable) — every row it inserts carries aggregate_type = NoteEvent.AGGREGATE_TYPE.
// append() also folds each event into notes_projection in the same transaction (via
// Note.apply, shared with the domain's own aggregate replay) — that's the entire mechanism
// keeping the read side in sync: no queue, no outbox, no async worker. A system that can't
// afford synchronous projection updates would swap this for an outbox-publishing variant
// behind the same NoteEventStore port, without NoteService or anything above it noticing.
@Repository
@Transactional
class ExposedNoteEventStore : NoteEventStore {

    override fun append(aggregateId: UUID, expectedVersion: Long, events: List<NoteEvent>) {
        val currentVersion = currentVersion(aggregateId)
        if (currentVersion != expectedVersion) {
            throw ConcurrentEventAppendException(aggregateId, expectedVersion, currentVersion)
        }

        var projected: Note? = findProjection(aggregateId)
        events.forEachIndexed { index, event ->
            val version = expectedVersion + index + 1
            val (eventType, payload) = NoteEventCodec.encode(event)
            EventsTable.insert {
                it[EventsTable.aggregateType] = NoteEvent.AGGREGATE_TYPE
                it[EventsTable.aggregateId] = aggregateId
                it[EventsTable.version] = version
                it[EventsTable.eventType] = eventType
                it[EventsTable.payload] = payload
                it[occurredAt] = event.occurredAt.atOffset(ZoneOffset.UTC)
            }
            projected = Note.apply(projected, event)
            upsertProjection(projected, version)
        }
    }

    override fun loadEvents(aggregateId: UUID): List<NoteEvent> =
        EventsTable.selectAll()
            .where { (EventsTable.aggregateType eq NoteEvent.AGGREGATE_TYPE) and (EventsTable.aggregateId eq aggregateId) }
            .orderBy(EventsTable.version, SortOrder.ASC)
            .map {
                NoteEventCodec.decode(
                    eventType = it[EventsTable.eventType],
                    aggregateId = it[EventsTable.aggregateId],
                    occurredAt = it[EventsTable.occurredAt].toInstant(),
                    payload = it[EventsTable.payload],
                )
            }

    // The upfront check above rejects the common case with a clear domain exception; the
    // table's UNIQUE (aggregate_type, aggregate_id, version) index is the actual safety net
    // if two transactions race between this check and their inserts.
    private fun currentVersion(aggregateId: UUID): Long =
        EventsTable.selectAll()
            .where { (EventsTable.aggregateType eq NoteEvent.AGGREGATE_TYPE) and (EventsTable.aggregateId eq aggregateId) }
            .maxOfOrNull { it[EventsTable.version] } ?: 0L
}
