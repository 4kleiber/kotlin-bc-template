package com.akl.bctemplate.storage.notes

import com.akl.bctemplate.domain.Page
import com.akl.bctemplate.domain.PageRequest
import com.akl.bctemplate.domain.notes.Note
import com.akl.bctemplate.domain.notes.NoteRepository
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

// Read-only: queries notes_projection, never writes to it. Every write to that table happens
// as a side effect of ExposedNoteEventStore.append() — see NoteRepository's port doc.
@Repository
@Transactional
class ExposedNoteRepository : NoteRepository {

    override fun findById(id: UUID): Note? = findProjection(id)

    override fun findAll(pageRequest: PageRequest): Page<Note> {
        val total = NotesProjectionTable.selectAll().count()
        val rows = NotesProjectionTable.selectAll()
            .orderBy(NotesProjectionTable.createdAt, SortOrder.DESC)
            .limit(pageRequest.size)
            .offset((pageRequest.page * pageRequest.size).toLong())
            .map { it.toNote() }
        val totalPages = if (total == 0L) 0 else ((total + pageRequest.size - 1) / pageRequest.size).toInt()
        return Page(rows, total, totalPages, pageRequest.page, pageRequest.size)
    }
}
