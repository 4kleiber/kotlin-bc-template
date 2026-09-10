package com.akl.bctemplate.domain.notes

import com.akl.bctemplate.domain.DomainEvent
import com.akl.bctemplate.domain.NewDomainEvent

// Maps NoteEvent to/from the generic (eventType, eventData) shape EventStore persists.
// Pure Kotlin Map manipulation — no JSON library needed here, since eventData is already a
// Map<String, Any?> at the domain/storage port boundary (see DomainEvent's doc); storage is
// the only layer that turns that Map into/out of actual JSON text.
object NoteEventCodec {
    const val STREAM_TYPE = "Note"

    fun encode(event: NoteEvent): NewDomainEvent = when (event) {
        is NoteCreated -> NewDomainEvent(
            eventType = "NoteCreated",
            eventData = mapOf("title" to event.title, "body" to event.body),
            occurredAt = event.occurredAt,
        )

        is NotePublished -> NewDomainEvent(eventType = "NotePublished", eventData = emptyMap(), occurredAt = event.occurredAt)
        is NoteArchived -> NewDomainEvent(eventType = "NoteArchived", eventData = emptyMap(), occurredAt = event.occurredAt)
    }

    fun decode(stored: DomainEvent): NoteEvent = when (stored.eventType) {
        "NoteCreated" -> NoteCreated(
            noteId = stored.streamId,
            title = stored.eventData["title"] as String,
            body = stored.eventData["body"] as String,
            occurredAt = stored.createdAt,
        )

        "NotePublished" -> NotePublished(stored.streamId, stored.createdAt)
        "NoteArchived" -> NoteArchived(stored.streamId, stored.createdAt)
        else -> error("Unknown note event type: ${stored.eventType}")
    }
}
