package com.akl.bctemplate.domain.notes

import com.akl.bctemplate.domain.Page
import com.akl.bctemplate.domain.PageRequest
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// One fake implementing both ports, exactly mirroring how ExposedNoteEventStore keeps the
// notes_projection table in sync with notes_events inside the same transaction: every append
// immediately folds the new event onto the projection via Note.apply — no mocking framework
// needed, per CLAUDE.md.
private class FakeNoteEventStore : NoteEventStore, NoteRepository {
    private val events = mutableMapOf<UUID, MutableList<NoteEvent>>()
    private val projections = mutableMapOf<UUID, Note>()

    override fun append(aggregateId: UUID, expectedVersion: Long, events: List<NoteEvent>) {
        val stream = this.events.getOrPut(aggregateId) { mutableListOf() }
        if (stream.size.toLong() != expectedVersion) {
            throw ConcurrentEventAppendException(aggregateId, expectedVersion, stream.size.toLong())
        }
        events.forEach { event ->
            stream.add(event)
            projections[aggregateId] = Note.apply(projections[aggregateId], event)
        }
    }

    override fun loadEvents(aggregateId: UUID): List<NoteEvent> = events[aggregateId].orEmpty()

    override fun findById(id: UUID): Note? = projections[id]

    override fun findAll(pageRequest: PageRequest): Page<Note> {
        val all = projections.values.toList()
        val fromIndex = (pageRequest.page * pageRequest.size).coerceAtMost(all.size)
        val toIndex = (fromIndex + pageRequest.size).coerceAtMost(all.size)
        val totalPages = if (all.isEmpty()) 0 else (all.size + pageRequest.size - 1) / pageRequest.size
        return Page(all.subList(fromIndex, toIndex), all.size.toLong(), totalPages, pageRequest.page, pageRequest.size)
    }
}

class NoteServiceTest {

    private fun newService(): Pair<NoteService, FakeNoteEventStore> {
        val store = FakeNoteEventStore()
        return NoteService(store, store) to store
    }

    @Test
    fun `createNote appends a NoteCreated event and returns the projected draft`() {
        val (service, store) = newService()

        val note = service.createNote("Groceries", "Milk, eggs, bread")

        assertEquals(NoteStatus.DRAFT, note.status)
        assertEquals(listOf(NoteCreated(note.id, "Groceries", "Milk, eggs, bread", note.createdAt)), store.loadEvents(note.id))
    }

    @Test
    fun `listNotes delegates pagination to the projection`() {
        val (service, _) = newService()
        service.createNote("First", "Body")
        service.createNote("Second", "Body")

        val page = service.listNotes(PageRequest(page = 0, size = 1))

        assertEquals(1, page.content.size)
        assertEquals(2, page.totalElements)
    }

    @Test
    fun `findNote returns the projected note when it exists`() {
        val (service, _) = newService()
        val created = service.createNote("Groceries", "Milk, eggs, bread")

        assertEquals(created, service.findNote(created.id))
    }

    @Test
    fun `findNote returns null when the note does not exist`() {
        val (service, _) = newService()

        assertNull(service.findNote(UUID.randomUUID()))
    }

    @Test
    fun `publishNote appends NotePublished and returns the projected note`() {
        val (service, store) = newService()
        val created = service.createNote("Groceries", "Milk, eggs, bread")

        val published = service.publishNote(created.id)

        assertTrue(published != null && published.status == NoteStatus.PUBLISHED)
        assertEquals(2, store.loadEvents(created.id).size)
    }

    @Test
    fun `publishNote returns null when the note does not exist`() {
        val (service, _) = newService()

        assertNull(service.publishNote(UUID.randomUUID()))
    }

    @Test
    fun `publishNote returns null when the note cannot transition to published`() {
        val (service, _) = newService()
        val created = service.createNote("Groceries", "Milk, eggs, bread")
        service.archiveNote(created.id)

        assertNull(service.publishNote(created.id))
    }

    @Test
    fun `archiveNote appends NoteArchived and returns the projected note`() {
        val (service, store) = newService()
        val created = service.createNote("Groceries", "Milk, eggs, bread")

        val archived = service.archiveNote(created.id)

        assertTrue(archived != null && archived.status == NoteStatus.ARCHIVED)
        assertEquals(2, store.loadEvents(created.id).size)
    }

    @Test
    fun `archiveNote returns null when the note does not exist`() {
        val (service, _) = newService()

        assertNull(service.archiveNote(UUID.randomUUID()))
    }
}
