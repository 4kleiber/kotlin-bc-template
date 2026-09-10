package com.akl.bctemplate.domain.notes

import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NoteTest {

    @Test
    fun `publish succeeds from DRAFT with a non-blank title and body`() {
        val note = Note(title = "Groceries", body = "Milk, eggs, bread")
        val now = Instant.parse("2026-01-01T00:00:00Z")

        val published = note.publish(now)

        assertTrue(published)
        assertEquals(NoteStatus.PUBLISHED, note.status)
        assertEquals(now, note.publishedAt)
    }

    @Test
    fun `publish fails when the title or body is blank`() {
        val note = Note(title = "  ", body = "Milk, eggs, bread")

        val published = note.publish()

        assertFalse(published)
        assertEquals(NoteStatus.DRAFT, note.status)
        assertNull(note.publishedAt)
    }

    @Test
    fun `publish fails when the note is not a DRAFT`() {
        val note = Note(title = "Groceries", body = "Milk, eggs, bread", status = NoteStatus.ARCHIVED)

        val published = note.publish()

        assertFalse(published)
        assertEquals(NoteStatus.ARCHIVED, note.status)
    }

    @Test
    fun `archive succeeds when the note is not already archived`() {
        val note = Note(title = "Groceries", body = "Milk, eggs, bread", status = NoteStatus.PUBLISHED)

        val archived = note.archive()

        assertTrue(archived)
        assertEquals(NoteStatus.ARCHIVED, note.status)
    }

    @Test
    fun `archive fails when the note is already archived`() {
        val note = Note(title = "Groceries", body = "Milk, eggs, bread", status = NoteStatus.ARCHIVED)

        val archived = note.archive()

        assertFalse(archived)
        assertEquals(NoteStatus.ARCHIVED, note.status)
    }
}
