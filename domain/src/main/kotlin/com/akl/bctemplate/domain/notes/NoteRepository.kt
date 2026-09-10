package com.akl.bctemplate.domain.notes

import com.akl.bctemplate.domain.Page
import com.akl.bctemplate.domain.PageRequest
import java.util.UUID

interface NoteRepository {
    fun save(note: Note): Note
    fun findAll(pageRequest: PageRequest): Page<Note>
    fun findById(id: UUID): Note?
}
