package com.akl.bctemplate.domain.notes

import com.akl.bctemplate.domain.EventStore
import com.akl.bctemplate.domain.Page
import com.akl.bctemplate.domain.PageRequest
import com.akl.bctemplate.domain.services.DomainService
import java.time.Instant
import java.util.UUID

@DomainService
class NoteService(private val eventStore: EventStore) {

    fun createNote(title: String, body: String): Note {
        val id = UUID.randomUUID()
        appendEvents(id, expectedVersion = 0, events = listOf(NoteCreated(id, title, body, Instant.now())))
        return findNote(id)!!
    }

    fun listNotes(pageRequest: PageRequest): Page<Note> {
        val streamIds = eventStore.listStreamIds(TENANT_ID, NoteEventCodec.STREAM_TYPE, pageRequest)
        val notes = streamIds.content.mapNotNull(::findNote)
        return Page(notes, streamIds.totalElements, streamIds.totalPages, streamIds.page, streamIds.size)
    }

    // Reconstructed fresh on every call — replaying a note's own event stream, not read from a
    // stored row. See EventStore's doc for why there's no persisted projection to keep in sync,
    // and for the snapshotting escape hatch if replay ever gets too slow for this to hold.
    fun findNote(id: UUID): Note? = Note.replay(loadEvents(id))

    fun publishNote(id: UUID): Note? = applyCommand(id) { it.publish() }

    fun archiveNote(id: UUID): Note? = applyCommand(id) { it.archive() }

    private fun applyCommand(id: UUID, command: (Note) -> NoteEvent?): Note? {
        val events = loadEvents(id)
        val note = Note.replay(events) ?: return null
        val event = command(note) ?: return null
        appendEvents(id, expectedVersion = events.size.toLong(), events = listOf(event))
        return findNote(id)
    }

    private fun loadEvents(id: UUID): List<NoteEvent> =
        eventStore.loadEvents(TENANT_ID, id, NoteEventCodec.STREAM_TYPE).map(NoteEventCodec::decode)

    private fun appendEvents(id: UUID, expectedVersion: Long, events: List<NoteEvent>) =
        eventStore.append(TENANT_ID, id, NoteEventCodec.STREAM_TYPE, expectedVersion, events.map(NoteEventCodec::encode))

    companion object {
        // This template has no multi-tenancy of its own — every Note lives under one
        // well-known tenant. A tenant-aware bounded context would instead thread a real
        // tenantId through these methods from whatever identifies the caller (e.g. the
        // authenticated user), rather than hardcoding it here.
        private val TENANT_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000000")
    }
}
