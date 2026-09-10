package com.akl.bctemplate.domain.notes

import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NoteTest {

    private val id = UUID.randomUUID()
    private val createdAt = Instant.parse("2026-01-01T00:00:00Z")

    private fun draftNote(title: String = "Groceries", body: String = "Milk, eggs, bread") =
        Note(id = id, title = title, body = body, status = NoteStatus.DRAFT, createdAt = createdAt)

    @Test
    fun `publish returns a NotePublished event from a valid draft`() {
        val note = draftNote()
        val now = Instant.parse("2026-01-02T00:00:00Z")

        val event = note.publish(now)

        assertEquals(NotePublished(id, now), event)
    }

    @Test
    fun `publish returns null when the title or body is blank`() {
        val note = draftNote(title = "  ")

        assertNull(note.publish())
    }

    @Test
    fun `publish returns null when the note is not a draft`() {
        val note = draftNote().copy(status = NoteStatus.ARCHIVED)

        assertNull(note.publish())
    }

    @Test
    fun `archive returns a NoteArchived event when not already archived`() {
        val note = draftNote().copy(status = NoteStatus.PUBLISHED)
        val now = Instant.parse("2026-01-03T00:00:00Z")

        val event = note.archive(now)

        assertEquals(NoteArchived(id, now), event)
    }

    @Test
    fun `archive returns null when already archived`() {
        val note = draftNote().copy(status = NoteStatus.ARCHIVED)

        assertNull(note.archive())
    }

    @Test
    fun `replay folds a single NoteCreated into a draft note`() {
        val created = NoteCreated(id, "Groceries", "Milk, eggs, bread", createdAt)

        val note = Note.replay(listOf(created))

        assertEquals(Note(id, "Groceries", "Milk, eggs, bread", NoteStatus.DRAFT, createdAt), note)
    }

    @Test
    fun `replay folds NoteCreated then NotePublished into a published note`() {
        val publishedAt = Instant.parse("2026-01-02T00:00:00Z")
        val events = listOf(
            NoteCreated(id, "Groceries", "Milk, eggs, bread", createdAt),
            NotePublished(id, publishedAt),
        )

        val note = Note.replay(events)

        assertEquals(NoteStatus.PUBLISHED, note?.status)
        assertEquals(publishedAt, note?.publishedAt)
    }

    @Test
    fun `replay returns null for an empty event list`() {
        assertNull(Note.replay(emptyList()))
    }
}
