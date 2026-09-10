package com.akl.bctemplate.storage.notes

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

// The source of truth: append-only, never updated or deleted. `id` is the global append order
// across every Note; `(aggregate_id, version)` is the per-Note sequence — loadEvents() replays
// it in `version` order, and its uniqueness is what makes NoteEventStore.append()'s optimistic
// concurrency check safe under concurrent writers.
internal object NoteEventsTable : Table("notes_events") {
    val id = long("id").autoIncrement()
    val aggregateId = javaUUID("aggregate_id")
    val version = long("version")
    val eventType = varchar("event_type", 100)
    val payload = text("payload")
    val occurredAt = timestampWithTimeZone("occurred_at")

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(aggregateId, version)
    }
}
