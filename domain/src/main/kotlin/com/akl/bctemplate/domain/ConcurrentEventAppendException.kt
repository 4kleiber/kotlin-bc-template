package com.akl.bctemplate.domain

import java.util.UUID

// Thrown by EventStore.append() when expectedVersion no longer matches a stream's actual
// event count — another command appended events to this stream in the meantime.
class ConcurrentEventAppendException(
    streamType: String,
    streamId: UUID,
    expectedVersion: Long,
    actualVersion: Long,
) : RuntimeException(
    "$streamType $streamId has $actualVersion event(s), but this command expected $expectedVersion — " +
        "it was modified concurrently; reload and retry.",
)
