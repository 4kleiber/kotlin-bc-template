package com.akl.bctemplate.domain.notes

import java.util.UUID

// Thrown by NoteEventStore.append() when expectedVersion no longer matches the aggregate's
// actual event count — another command appended events to this Note in the meantime.
class ConcurrentEventAppendException(
    aggregateId: UUID,
    expectedVersion: Long,
    actualVersion: Long,
) : RuntimeException(
    "Note $aggregateId has $actualVersion event(s), but this command expected $expectedVersion — " +
        "it was modified concurrently; reload and retry.",
)
