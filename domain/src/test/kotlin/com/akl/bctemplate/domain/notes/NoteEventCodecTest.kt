package com.akl.bctemplate.domain.notes

import com.akl.bctemplate.domain.DomainEvent
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NoteEventCodecTest {

    private val noteId = UUID.randomUUID()
    private val occurredAt = Instant.parse("2026-01-01T00:00:00Z")

    private fun stored(eventType: String, eventData: Map<String, Any?>) = DomainEvent(
        id = UUID.randomUUID(),
        tenantId = UUID.randomUUID(),
        streamId = noteId,
        streamType = NoteEventCodec.STREAM_TYPE,
        version = 1,
        eventType = eventType,
        eventData = eventData,
        metadata = null,
        createdAt = occurredAt,
        sequenceNumber = 1,
    )

    @Test
    fun `NoteCreated round-trips through the generic envelope`() {
        val event = NoteCreated(noteId, "Groceries", "Milk, eggs, bread", occurredAt)

        val encoded = NoteEventCodec.encode(event)
        val decoded = NoteEventCodec.decode(stored(encoded.eventType, encoded.eventData))

        assertEquals(event, decoded)
        assertEquals("NoteCreated", encoded.eventType)
        assertEquals(mapOf("title" to "Groceries", "body" to "Milk, eggs, bread"), encoded.eventData)
    }

    @Test
    fun `NotePublished round-trips through the generic envelope`() {
        val event = NotePublished(noteId, occurredAt)

        val encoded = NoteEventCodec.encode(event)
        val decoded = NoteEventCodec.decode(stored(encoded.eventType, encoded.eventData))

        assertEquals(event, decoded)
        assertEquals(emptyMap(), encoded.eventData)
    }

    @Test
    fun `NoteArchived round-trips through the generic envelope`() {
        val event = NoteArchived(noteId, occurredAt)

        val encoded = NoteEventCodec.encode(event)
        val decoded = NoteEventCodec.decode(stored(encoded.eventType, encoded.eventData))

        assertEquals(event, decoded)
        assertEquals(emptyMap(), encoded.eventData)
    }

    @Test
    fun `decode throws on an unrecognized event type`() {
        assertFailsWith<IllegalStateException> {
            NoteEventCodec.decode(stored("SomethingElse", emptyMap()))
        }
    }
}
