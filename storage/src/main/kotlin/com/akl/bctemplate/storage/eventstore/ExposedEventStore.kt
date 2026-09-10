package com.akl.bctemplate.storage.eventstore

import com.akl.bctemplate.domain.ConcurrentEventAppendException
import com.akl.bctemplate.domain.DomainEvent
import com.akl.bctemplate.domain.EventStore
import com.akl.bctemplate.domain.NewDomainEvent
import com.akl.bctemplate.domain.Page
import com.akl.bctemplate.domain.PageRequest
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.ZoneOffset
import java.util.UUID

// The one EventStore implementation for every bounded context: nothing here knows about
// "Note" or any other stream_type — that's just a string parameter, supplied by whichever
// bounded context's codec is calling. There is deliberately no per-bounded-context adapter
// class (contrast with a hypothetical "ExposedNoteEventStore").
@Repository
@Transactional
class ExposedEventStore : EventStore {

    override fun append(tenantId: UUID, streamId: UUID, streamType: String, expectedVersion: Long, events: List<NewDomainEvent>) {
        val currentVersion = currentVersion(tenantId, streamId, streamType)
        if (currentVersion != expectedVersion) {
            throw ConcurrentEventAppendException(streamType, streamId, expectedVersion, currentVersion)
        }
        events.forEachIndexed { index, event ->
            EventsTable.insert {
                it[EventsTable.id] = UUID.randomUUID()
                it[EventsTable.tenantId] = tenantId
                it[EventsTable.streamId] = streamId
                it[EventsTable.streamType] = streamType
                it[EventsTable.version] = expectedVersion + index + 1
                it[EventsTable.eventType] = event.eventType
                it[EventsTable.eventData] = event.eventData
                it[EventsTable.metadata] = event.metadata
                it[createdAt] = event.occurredAt.atOffset(ZoneOffset.UTC)
            }
        }
    }

    override fun loadEvents(tenantId: UUID, streamId: UUID, streamType: String): List<DomainEvent> =
        EventsTable.selectAll()
            .where {
                (EventsTable.tenantId eq tenantId) and
                    (EventsTable.streamId eq streamId) and
                    (EventsTable.streamType eq streamType)
            }
            .orderBy(EventsTable.version, SortOrder.ASC)
            .map { it.toDomainEvent() }

    // The only way to discover which streams exist for a type — there is no projection table
    // listing them. Distinct-then-paginate-in-memory keeps this simple for the template; a
    // high-volume stream_type in a real system would want an index-only scan or a
    // materialized "known streams" table instead of scanning every event of that type.
    override fun listStreamIds(tenantId: UUID, streamType: String, pageRequest: PageRequest): Page<UUID> {
        val ids = EventsTable.select(EventsTable.streamId)
            .where { (EventsTable.tenantId eq tenantId) and (EventsTable.streamType eq streamType) }
            .withDistinct()
            .orderBy(EventsTable.streamId, SortOrder.ASC)
            .map { it[EventsTable.streamId] }

        val fromIndex = (pageRequest.page * pageRequest.size).coerceAtMost(ids.size)
        val toIndex = (fromIndex + pageRequest.size).coerceAtMost(ids.size)
        val totalPages = if (ids.isEmpty()) 0 else (ids.size + pageRequest.size - 1) / pageRequest.size
        return Page(ids.subList(fromIndex, toIndex), ids.size.toLong(), totalPages, pageRequest.page, pageRequest.size)
    }

    // The upfront check above rejects the common case with a clear domain exception; the
    // table's UNIQUE (tenant_id, stream_id, version) index is the actual safety net if two
    // transactions race between this check and their inserts.
    private fun currentVersion(tenantId: UUID, streamId: UUID, streamType: String): Long =
        EventsTable.selectAll()
            .where {
                (EventsTable.tenantId eq tenantId) and
                    (EventsTable.streamId eq streamId) and
                    (EventsTable.streamType eq streamType)
            }
            .maxOfOrNull { it[EventsTable.version] } ?: 0L

    private fun ResultRow.toDomainEvent() = DomainEvent(
        id = this[EventsTable.id],
        tenantId = this[EventsTable.tenantId],
        streamId = this[EventsTable.streamId],
        streamType = this[EventsTable.streamType],
        version = this[EventsTable.version],
        eventType = this[EventsTable.eventType],
        eventData = this[EventsTable.eventData],
        metadata = this[EventsTable.metadata],
        createdAt = this[EventsTable.createdAt].toInstant(),
        sequenceNumber = this[EventsTable.sequenceNumber],
    )
}
