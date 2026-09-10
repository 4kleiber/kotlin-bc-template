package com.akl.bctemplate.domain.notes

import com.akl.bctemplate.domain.Page
import com.akl.bctemplate.domain.PageRequest
import java.util.UUID

// Read-side port: a derived, rebuildable projection of NoteEventStore's event log — never the
// source of truth. In this template it's kept in sync inside the same transaction as every
// append (see storage's ExposedNoteEventStore), so callers can treat it like an ordinary query
// repository; a system that projects asynchronously would read eventually-consistent state
// through this same interface instead. Note the absence of save()/delete(): nothing outside
// storage ever writes to a projection directly — only the event store does, as a side effect
// of appending.
interface NoteRepository {
    fun findById(id: UUID): Note?
    fun findAll(pageRequest: PageRequest): Page<Note>
}
