package com.akl.bctemplate.domain.notes

import com.akl.bctemplate.domain.ConcurrentEventAppendException
import com.akl.bctemplate.domain.DomainEvent
import com.akl.bctemplate.domain.EventStore
import com.akl.bctemplate.domain.NewDomainEvent
import com.akl.bctemplate.domain.Page
import com.akl.bctemplate.domain.PageRequest
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// The same generic EventStore NoteService talks to in production — there is no
// bounded-context-specific store to fake against, per CLAUDE.md's hand-written-fake rule.
private class FakeEventStore : EventStore {
    private val streams = mutableMapOf<Pair<UUID, String>, MutableList<DomainEvent>>()
    private var nextSequence = 1L

    override fun append(tenantId: UUID, streamId: UUID, streamType: String, expectedVersion: Long, events: List<NewDomainEvent>) {
        val key = streamId to streamType
        val stream = streams.getOrPut(key) { mutableListOf() }
        if (stream.size.toLong() != expectedVersion) {
            throw ConcurrentEventAppendException(streamType, streamId, expectedVersion, stream.size.toLong())
        }
        events.forEach { event ->
            stream.add(
                DomainEvent(
                    id = UUID.randomUUID(),
                    tenantId = tenantId,
                    streamId = streamId,
                    streamType = streamType,
                    version = stream.size.toLong() + 1,
                    eventType = event.eventType,
                    eventData = event.eventData,
                    metadata = event.metadata,
                    createdAt = event.occurredAt,
                    sequenceNumber = nextSequence++,
                ),
            )
        }
    }

    override fun loadEvents(tenantId: UUID, streamId: UUID, streamType: String): List<DomainEvent> =
        streams[streamId to streamType].orEmpty()

    override fun listStreamIds(tenantId: UUID, streamType: String, pageRequest: PageRequest): Page<UUID> {
        val ids = streams.keys.filter { it.second == streamType }.map { it.first }
        val fromIndex = (pageRequest.page * pageRequest.size).coerceAtMost(ids.size)
        val toIndex = (fromIndex + pageRequest.size).coerceAtMost(ids.size)
        val totalPages = if (ids.isEmpty()) 0 else (ids.size + pageRequest.size - 1) / pageRequest.size
        return Page(ids.subList(fromIndex, toIndex), ids.size.toLong(), totalPages, pageRequest.page, pageRequest.size)
    }
}

class NoteServiceTest {

    private fun newService() = NoteService(FakeEventStore())

    @Test
    fun `createNote appends a NoteCreated event and returns the replayed draft`() {
        val service = newService()

        val note = service.createNote("Groceries", "Milk, eggs, bread")

        assertEquals(NoteStatus.DRAFT, note.status)
        assertEquals("Groceries", note.title)
    }

    @Test
    fun `listNotes delegates pagination to the event store's stream index`() {
        val service = newService()
        service.createNote("First", "Body")
        service.createNote("Second", "Body")

        val page = service.listNotes(PageRequest(page = 0, size = 1))

        assertEquals(1, page.content.size)
        assertEquals(2, page.totalElements)
    }

    @Test
    fun `findNote replays the note's events when it exists`() {
        val service = newService()
        val created = service.createNote("Groceries", "Milk, eggs, bread")

        assertEquals(created, service.findNote(created.id))
    }

    @Test
    fun `findNote returns null when the note does not exist`() {
        val service = newService()

        assertNull(service.findNote(UUID.randomUUID()))
    }

    @Test
    fun `publishNote appends NotePublished and returns the replayed note`() {
        val service = newService()
        val created = service.createNote("Groceries", "Milk, eggs, bread")

        val published = service.publishNote(created.id)

        assertTrue(published != null && published.status == NoteStatus.PUBLISHED)
    }

    @Test
    fun `publishNote returns null when the note does not exist`() {
        val service = newService()

        assertNull(service.publishNote(UUID.randomUUID()))
    }

    @Test
    fun `publishNote returns null when the note cannot transition to published`() {
        val service = newService()
        val created = service.createNote("Groceries", "Milk, eggs, bread")
        service.archiveNote(created.id)

        assertNull(service.publishNote(created.id))
    }

    @Test
    fun `archiveNote appends NoteArchived and returns the replayed note`() {
        val service = newService()
        val created = service.createNote("Groceries", "Milk, eggs, bread")

        val archived = service.archiveNote(created.id)

        assertTrue(archived != null && archived.status == NoteStatus.ARCHIVED)
    }

    @Test
    fun `archiveNote returns null when the note does not exist`() {
        val service = newService()

        assertNull(service.archiveNote(UUID.randomUUID()))
    }
}
