package com.akl.bctemplate.domain.notes

import com.akl.bctemplate.domain.DomainEvent
import java.time.Instant
import java.util.UUID

// Facts, not commands: named in the past tense, and once appended, never changed or removed.
// Extends the shared DomainEvent directly — a NoteCreated/NotePublished/NoteArchived instance
// already *is* a DomainEvent, handed straight to EventStore.append() with no translation
// step. `eventType` is passed as an explicit string (not derived from the class name) so a
// class can be renamed later without changing what's stored — see NoteEventDecoder for the
// read-back direction.
sealed class NoteEvent(streamId: UUID, eventType: String, occurredAt: Instant) :
    DomainEvent(streamId = streamId, streamType = STREAM_TYPE, eventType = eventType, occurredAt = occurredAt) {

    val noteId: UUID get() = streamId

    companion object {
        const val STREAM_TYPE = "Note"
    }
}

data class NoteCreated(
    override val streamId: UUID,
    val title: String,
    val body: String,
    override val occurredAt: Instant,
) : NoteEvent(streamId, "NoteCreated", occurredAt) {
    override fun data(): Map<String, Any?> = mapOf("title" to title, "body" to body)
}

data class NotePublished(
    override val streamId: UUID,
    override val occurredAt: Instant,
) : NoteEvent(streamId, "NotePublished", occurredAt) {
    override fun data(): Map<String, Any?> = emptyMap()
}

data class NoteArchived(
    override val streamId: UUID,
    override val occurredAt: Instant,
) : NoteEvent(streamId, "NoteArchived", occurredAt) {
    override fun data(): Map<String, Any?> = emptyMap()
}
