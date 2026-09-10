package com.akl.bctemplate.storage.notes

import com.akl.bctemplate.domain.Page
import com.akl.bctemplate.domain.PageRequest
import com.akl.bctemplate.domain.notes.Note
import com.akl.bctemplate.domain.notes.NoteRepository
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

@Repository
@Transactional
class ExposedNoteRepository : NoteRepository {

    override fun save(note: Note): Note {
        val id = note.id ?: UUID.randomUUID()
        val createdAt = note.createdAt ?: Instant.now()
        val exists = NotesTable.selectAll().where { NotesTable.id eq id }.count() > 0
        if (exists) {
            NotesTable.update({ NotesTable.id eq id }) {
                it[title] = note.title
                it[body] = note.body
                it[status] = note.status
                it[publishedAt] = note.publishedAt?.atOffset(ZoneOffset.UTC)
            }
        } else {
            NotesTable.insert {
                it[NotesTable.id] = id
                it[title] = note.title
                it[body] = note.body
                it[status] = note.status
                it[NotesTable.createdAt] = createdAt.atOffset(ZoneOffset.UTC)
                it[publishedAt] = note.publishedAt?.atOffset(ZoneOffset.UTC)
            }
        }
        return note.copy(id = id, createdAt = createdAt)
    }

    override fun findAll(pageRequest: PageRequest): Page<Note> {
        val total = NotesTable.selectAll().count()
        val rows = NotesTable.selectAll()
            .orderBy(NotesTable.createdAt, SortOrder.DESC)
            .limit(pageRequest.size)
            .offset((pageRequest.page * pageRequest.size).toLong())
            .map { it.toNote() }
        val totalPages = if (total == 0L) 0 else ((total + pageRequest.size - 1) / pageRequest.size).toInt()
        return Page(rows, total, totalPages, pageRequest.page, pageRequest.size)
    }

    override fun findById(id: UUID): Note? =
        NotesTable.selectAll().where { NotesTable.id eq id }.singleOrNull()?.toNote()

    private fun ResultRow.toNote() = Note(
        id = this[NotesTable.id],
        title = this[NotesTable.title],
        body = this[NotesTable.body],
        status = this[NotesTable.status],
        createdAt = this[NotesTable.createdAt].toInstant(),
        publishedAt = this[NotesTable.publishedAt]?.toInstant(),
    )
}
