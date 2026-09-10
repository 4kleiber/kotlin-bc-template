package com.akl.bctemplate.domain.notes

import com.akl.bctemplate.domain.Page
import com.akl.bctemplate.domain.PageRequest
import com.akl.bctemplate.domain.services.DomainService
import java.util.UUID

@DomainService
class NoteService(private val noteRepository: NoteRepository) {

    fun createNote(title: String, body: String): Note =
        noteRepository.save(Note(title = title, body = body))

    fun listNotes(pageRequest: PageRequest): Page<Note> = noteRepository.findAll(pageRequest)

    fun findNote(id: UUID): Note? = noteRepository.findById(id)

    fun publishNote(id: UUID): Note? {
        val note = noteRepository.findById(id) ?: return null
        return if (note.publish()) noteRepository.save(note) else null
    }

    fun archiveNote(id: UUID): Note? {
        val note = noteRepository.findById(id) ?: return null
        return if (note.archive()) noteRepository.save(note) else null
    }
}
