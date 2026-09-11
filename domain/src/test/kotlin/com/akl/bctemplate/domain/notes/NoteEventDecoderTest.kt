package com.akl.bctemplate.domain.notes

import com.akl.bctemplate.domain.StoredEvent
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NoteEventDecoderTest {

    private val noteId = UUID.randomUUID()
    private val occurredAt = Instant.parse("2026-01-01T00:00:00Z")

    // Simulates what EventStore.loadEvents() hands back: the event's own eventType/data(),
    // wrapped with the row metadata a real store would have assigned.
    private fun stored(event: NoteEvent) = StoredEvent(
        streamId = event.streamId,
        streamType = event.streamType,
        eventType = event.eventType,
        occurredAt = event.occurredAt,
        metadata = event.metadata,
        id = UUID.randomUUID(),
        tenantId = UUID.randomUUID(),
        version = 1,
        sequenceNumber = 1,
        eventData = event.data(),
    )

    @Test
    fun `decodes a stored NoteCreated back into the same event`() {
        val event = NoteCreated(noteId, "Groceries", "Milk, eggs, bread", occurredAt)

        assertEquals(event, NoteEventDecoder.decode(stored(event)))
    }

    @Test
    fun `decodes a stored NotePublished back into the same event`() {
        val event = NotePublished(noteId, occurredAt)

        assertEquals(event, NoteEventDecoder.decode(stored(event)))
    }

    @Test
    fun `decodes a stored NoteArchived back into the same event`() {
        val event = NoteArchived(noteId, occurredAt)

        assertEquals(event, NoteEventDecoder.decode(stored(event)))
    }

    @Test
    fun `decode throws on an unrecognized event type`() {
        val unknown = stored(NotePublished(noteId, occurredAt)).copy(eventType = "SomethingElse")

        assertFailsWith<IllegalStateException> {
            NoteEventDecoder.decode(unknown)
        }
    }
}
