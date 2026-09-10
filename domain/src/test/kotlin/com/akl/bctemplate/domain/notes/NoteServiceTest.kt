package com.akl.bctemplate.domain.notes

import com.akl.bctemplate.domain.Page
import com.akl.bctemplate.domain.PageRequest
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Hand-written in-memory fake, per CLAUDE.md: domain-level unit tests use fakes, not a mocking framework.
private class FakeNoteRepository : NoteRepository {
    private val notes = mutableMapOf<UUID, Note>()

    override fun save(note: Note): Note {
        val saved = if (note.id == null) note.copy(id = UUID.randomUUID()) else note
        notes[saved.id!!] = saved
        return saved
    }

    override fun findAll(pageRequest: PageRequest): Page<Note> {
        val all = notes.values.toList()
        val fromIndex = (pageRequest.page * pageRequest.size).coerceAtMost(all.size)
        val toIndex = (fromIndex + pageRequest.size).coerceAtMost(all.size)
        val totalPages = if (all.isEmpty()) 0 else (all.size + pageRequest.size - 1) / pageRequest.size
        return Page(
            content = all.subList(fromIndex, toIndex),
            totalElements = all.size.toLong(),
            totalPages = totalPages,
            page = pageRequest.page,
            size = pageRequest.size,
        )
    }

    override fun findById(id: UUID): Note? = notes[id]
}

class NoteServiceTest {

    @Test
    fun `createNote saves a new draft note`() {
        val service = NoteService(FakeNoteRepository())

        val note = service.createNote("Groceries", "Milk, eggs, bread")

        assertEquals(NoteStatus.DRAFT, note.status)
        assertEquals("Groceries", note.title)
    }

    @Test
    fun `listNotes delegates pagination to the repository`() {
        val repository = FakeNoteRepository()
        val service = NoteService(repository)
        service.createNote("First", "Body")
        service.createNote("Second", "Body")

        val page = service.listNotes(PageRequest(page = 0, size = 1))

        assertEquals(1, page.content.size)
        assertEquals(2, page.totalElements)
    }

    @Test
    fun `findNote returns the note when it exists`() {
        val repository = FakeNoteRepository()
        val service = NoteService(repository)
        val created = service.createNote("Groceries", "Milk, eggs, bread")

        val found = service.findNote(created.id!!)

        assertEquals(created, found)
    }

    @Test
    fun `findNote returns null when the note does not exist`() {
        val service = NoteService(FakeNoteRepository())

        val found = service.findNote(UUID.randomUUID())

        assertNull(found)
    }

    @Test
    fun `publishNote publishes an existing draft`() {
        val repository = FakeNoteRepository()
        val service = NoteService(repository)
        val created = service.createNote("Groceries", "Milk, eggs, bread")

        val published = service.publishNote(created.id!!)

        assertTrue(published != null && published.status == NoteStatus.PUBLISHED)
        assertEquals(NoteStatus.PUBLISHED, repository.findById(created.id)?.status)
    }

    @Test
    fun `publishNote returns null when the note does not exist`() {
        val service = NoteService(FakeNoteRepository())

        val published = service.publishNote(UUID.randomUUID())

        assertNull(published)
    }

    @Test
    fun `publishNote returns null when the note cannot transition to published`() {
        val repository = FakeNoteRepository()
        val service = NoteService(repository)
        val created = service.createNote("Groceries", "Milk, eggs, bread")
        service.archiveNote(created.id!!)

        val published = service.publishNote(created.id)

        assertNull(published)
    }

    @Test
    fun `archiveNote archives an existing note`() {
        val repository = FakeNoteRepository()
        val service = NoteService(repository)
        val created = service.createNote("Groceries", "Milk, eggs, bread")

        val archived = service.archiveNote(created.id!!)

        assertTrue(archived != null && archived.status == NoteStatus.ARCHIVED)
        assertEquals(NoteStatus.ARCHIVED, repository.findById(created.id)?.status)
    }

    @Test
    fun `archiveNote returns null when the note does not exist`() {
        val service = NoteService(FakeNoteRepository())

        val archived = service.archiveNote(UUID.randomUUID())

        assertNull(archived)
    }
}
