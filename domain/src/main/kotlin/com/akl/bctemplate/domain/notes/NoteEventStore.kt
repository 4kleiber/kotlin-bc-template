package com.akl.bctemplate.domain.notes

import java.util.UUID

// Write-side port: the append-only source of truth for a Note. `expectedVersion` is how many
// events the caller already knows about for this aggregate (0 for a brand-new one) — an
// optimistic-concurrency guard against two commands racing on the same Note; see
// ConcurrentEventAppendException. NoteRepository (read side) is a derived projection of this
// store, not an alternative to it — see NoteRepository's doc.
interface NoteEventStore {
    fun append(aggregateId: UUID, expectedVersion: Long, events: List<NoteEvent>)
    fun loadEvents(aggregateId: UUID): List<NoteEvent>
}
