package com.akl.bctemplate.domain

import java.util.UUID

// A hand-written in-memory fake of the one generic EventStore port — reusable as-is by any
// bounded context's @DomainService tests (see notes.NoteServiceTest for a worked example),
// since EventStore itself is generic across stream types. No mocking framework needed, per
// CLAUDE.md's Integration Tests / domain-unit-test conventions.
class FakeEventStore : EventStore {
    private val streams = mutableMapOf<Pair<UUID, String>, MutableList<StoredEvent>>()
    private var nextSequence = 1L

    override fun append(tenantId: UUID, streamId: UUID, streamType: String, expectedVersion: Long, events: List<DomainEvent>) {
        val key = streamId to streamType
        val stream = streams.getOrPut(key) { mutableListOf() }
        if (stream.size.toLong() != expectedVersion) {
            throw ConcurrentEventAppendException(streamType, streamId, expectedVersion, stream.size.toLong())
        }
        events.forEach { event ->
            stream.add(
                StoredEvent(
                    streamId = streamId,
                    streamType = streamType,
                    eventType = event.eventType,
                    occurredAt = event.occurredAt,
                    metadata = event.metadata,
                    id = UUID.randomUUID(),
                    tenantId = tenantId,
                    version = stream.size.toLong() + 1,
                    sequenceNumber = nextSequence++,
                    eventData = event.data(),
                ),
            )
        }
    }

    override fun loadEvents(tenantId: UUID, streamId: UUID, streamType: String): List<StoredEvent> =
        streams[streamId to streamType].orEmpty()

    override fun listStreamIds(tenantId: UUID, streamType: String, pageRequest: PageRequest): Page<UUID> {
        val ids = streams.keys.filter { it.second == streamType }.map { it.first }
        val fromIndex = (pageRequest.page * pageRequest.size).coerceAtMost(ids.size)
        val toIndex = (fromIndex + pageRequest.size).coerceAtMost(ids.size)
        val totalPages = if (ids.isEmpty()) 0 else (ids.size + pageRequest.size - 1) / pageRequest.size
        return Page(ids.subList(fromIndex, toIndex), ids.size.toLong(), totalPages, pageRequest.page, pageRequest.size)
    }
}
