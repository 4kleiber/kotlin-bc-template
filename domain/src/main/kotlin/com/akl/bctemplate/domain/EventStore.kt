package com.akl.bctemplate.domain

import java.util.UUID

// One event store, shared by every bounded context — there is no per-bounded-context event
// store type (e.g. no "NoteEventStore"). append()/loadEvents() know nothing about "Note" or
// any other stream: `streamType` is just a string a @DomainService supplies (see
// notes.NoteEventCodec.STREAM_TYPE), scoping which bounded context's events a call concerns.
//
// There is deliberately no read/projection port alongside this one: a bounded context
// reconstructs its current state on the fly by loadEvents() + its own aggregate's replay
// (see notes.Note.replay), every time it's queried — not from a persisted, kept-in-sync read
// model. If replaying gets expensive, the answer is a snapshot of one stream's folded state
// at a known version (letting replay resume from there instead of from scratch) — deliberately
// not implemented here; add it if and when it's actually needed.
interface EventStore {
    // `expectedVersion` is how many events the caller already knows about for this stream (0
    // for a brand-new one) — an optimistic-concurrency guard against two commands racing on
    // the same stream; see ConcurrentEventAppendException.
    fun append(tenantId: UUID, streamId: UUID, streamType: String, expectedVersion: Long, events: List<NewDomainEvent>)

    fun loadEvents(tenantId: UUID, streamId: UUID, streamType: String): List<DomainEvent>

    // The only way to discover which streams exist for a type, since there's no projection
    // table listing them — used to page through "all Notes" without replaying the entire
    // event log. Ordering is left to the implementation; callers needing a specific order
    // should sort after loading, not assume one.
    fun listStreamIds(tenantId: UUID, streamType: String, pageRequest: PageRequest): Page<UUID>
}
