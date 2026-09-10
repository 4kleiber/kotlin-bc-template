package com.akl.bctemplate.domain.notes

import java.time.Instant
import java.util.UUID

// A rich domain model: the DRAFT -> PUBLISHED -> ARCHIVED lifecycle is enforced here, not in
// NoteService or the controller, so the invariant holds no matter which adapter calls into it.
data class Note(
    val id: UUID? = null,
    val title: String,
    val body: String,
    var status: NoteStatus = NoteStatus.DRAFT,
    val createdAt: Instant? = null,
    var publishedAt: Instant? = null,
) {
    fun publish(now: Instant = Instant.now()): Boolean {
        if (status != NoteStatus.DRAFT) return false
        if (title.isBlank() || body.isBlank()) return false
        status = NoteStatus.PUBLISHED
        publishedAt = now
        return true
    }

    fun archive(): Boolean {
        if (status == NoteStatus.ARCHIVED) return false
        status = NoteStatus.ARCHIVED
        return true
    }
}
