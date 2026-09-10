package com.akl.bctemplate.domain.notes

import com.akl.bctemplate.domain.Page
import com.akl.bctemplate.domain.PageRequest
import com.akl.bctemplate.domain.services.DomainService
import java.time.Instant
import java.util.UUID

@DomainService
class NoteService(
    private val eventStore: NoteEventStore,
    private val noteRepository: NoteRepository,
) {

    fun createNote(title: String, body: String): Note {
        val id = UUID.randomUUID()
        eventStore.append(id, expectedVersion = 0, events = listOf(NoteCreated(id, title, body, Instant.now())))
        return noteRepository.findById(id)!!
    }

    fun listNotes(pageRequest: PageRequest): Page<Note> = noteRepository.findAll(pageRequest)

    fun findNote(id: UUID): Note? = noteRepository.findById(id)

    fun publishNote(id: UUID): Note? = applyCommand(id) { it.publish() }

    fun archiveNote(id: UUID): Note? = applyCommand(id) { it.archive() }

    // Load the event stream, replay it to decide the current state, ask the aggregate for the
    // next event (or bail if the command doesn't apply), then append — the read-your-writes
    // return value comes back through the projection, exactly like the old save()-returning-
    // the-saved-row pattern did.
    private fun applyCommand(id: UUID, command: (Note) -> NoteEvent?): Note? {
        val events = eventStore.loadEvents(id)
        val note = Note.replay(events) ?: return null
        val event = command(note) ?: return null
        eventStore.append(id, expectedVersion = events.size.toLong(), events = listOf(event))
        return noteRepository.findById(id)
    }
}
