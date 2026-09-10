package com.akl.bctemplate.storage.notes

import com.akl.bctemplate.domain.notes.NoteStatus
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

internal object NotesTable : Table("notes") {
    val id = javaUUID("id")
    val title = varchar("title", 255)
    val body = text("body")
    val status = enumerationByName<NoteStatus>("status", 20)
    val createdAt = timestampWithTimeZone("created_at")
    val publishedAt = timestampWithTimeZone("published_at").nullable()

    override val primaryKey = PrimaryKey(id)
}
