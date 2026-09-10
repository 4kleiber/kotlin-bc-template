package com.akl.bctemplate.storage.notes

import com.akl.bctemplate.domain.notes.Note
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.ZoneOffset
import java.util.UUID

// Shared by ExposedNoteEventStore (keeps notes_projection live, one event at a time, right
// after appending it) and NoteProjectionRebuilder (rebuilds it from scratch): both just need
// to turn a Note folded via Note.apply() into a notes_projection row.

internal fun findProjection(id: UUID): Note? =
    NotesProjectionTable.selectAll().where { NotesProjectionTable.id eq id }.singleOrNull()?.toNote()

internal fun upsertProjection(note: Note, version: Long) {
    val exists = NotesProjectionTable.selectAll().where { NotesProjectionTable.id eq note.id }.count() > 0
    if (exists) {
        NotesProjectionTable.update({ NotesProjectionTable.id eq note.id }) {
            it[title] = note.title
            it[body] = note.body
            it[status] = note.status
            it[publishedAt] = note.publishedAt?.atOffset(ZoneOffset.UTC)
            it[NotesProjectionTable.version] = version
        }
    } else {
        NotesProjectionTable.insert {
            it[id] = note.id
            it[title] = note.title
            it[body] = note.body
            it[status] = note.status
            it[createdAt] = note.createdAt.atOffset(ZoneOffset.UTC)
            it[publishedAt] = note.publishedAt?.atOffset(ZoneOffset.UTC)
            it[NotesProjectionTable.version] = version
        }
    }
}

internal fun ResultRow.toNote() = Note(
    id = this[NotesProjectionTable.id],
    title = this[NotesProjectionTable.title],
    body = this[NotesProjectionTable.body],
    status = this[NotesProjectionTable.status],
    createdAt = this[NotesProjectionTable.createdAt].toInstant(),
    publishedAt = this[NotesProjectionTable.publishedAt]?.toInstant(),
)
