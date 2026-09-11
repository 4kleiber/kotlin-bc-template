package com.akl.bctemplate.domain.notes

import com.akl.bctemplate.domain.StoredEvent

// Turns a generic, already-persisted StoredEvent back into a typed NoteEvent for replay. This
// is the one direction that can't be eliminated by NoteEvent extending DomainEvent directly
// (see DomainEvent's doc): reconstructing a specific Kotlin type from a generic row always
// needs code that knows that type, and that code has to live here — in the notes bounded
// context — not in storage, which only ever handles DomainEvent/StoredEvent.
object NoteEventDecoder {
    fun decode(stored: StoredEvent): NoteEvent = when (stored.eventType) {
        "NoteCreated" -> NoteCreated(
            streamId = stored.streamId,
            title = stored.data()["title"] as String,
            body = stored.data()["body"] as String,
            occurredAt = stored.occurredAt,
        )

        "NotePublished" -> NotePublished(stored.streamId, stored.occurredAt)
        "NoteArchived" -> NoteArchived(stored.streamId, stored.occurredAt)
        else -> error("Unknown note event type: ${stored.eventType}")
    }
}
