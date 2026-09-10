package com.akl.bctemplate.storage.notes

import com.akl.bctemplate.domain.notes.NoteStatus
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

// The read side: a denormalized, disposable projection of notes_events, kept live by
// ExposedNoteEventStore and fully rebuildable by NoteProjectionRebuilder — it holds nothing
// that notes_events doesn't already contain. `version` records the notes_events.version last
// folded into this row (useful for spotting projection lag in a system that projects
// asynchronously; this template updates it synchronously, in the same transaction as the event).
internal object NotesProjectionTable : Table("notes_projection") {
    val id = javaUUID("id")
    val title = varchar("title", 255)
    val body = text("body")
    val status = enumerationByName<NoteStatus>("status", 20)
    val createdAt = timestampWithTimeZone("created_at")
    val publishedAt = timestampWithTimeZone("published_at").nullable()
    val version = long("version")

    override val primaryKey = PrimaryKey(id)
}
