package com.akl.bctemplate.storage.eventstore

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

// The single, system-wide event log: every bounded context's ExposedXEventStore appends its
// facts here, tagged with `aggregate_type` (e.g. "Note"), all sharing one global `id`
// sequence — the exact order every event was ever created in, across the whole system, not
// just within one aggregate or one bounded context. A per-context table would each get its
// own independent auto-increment sequence, losing that cross-context ordering entirely.
//
// `(aggregate_type, aggregate_id, version)` is the per-aggregate sequence — unique, which is
// what makes NoteEventStore.append()'s optimistic concurrency check safe under concurrent
// writers — and is what loadEvents() replays in `version` order.
internal object EventsTable : Table("events") {
    val id = long("id").autoIncrement()
    val aggregateType = varchar("aggregate_type", 100)
    val aggregateId = javaUUID("aggregate_id")
    val version = long("version")
    val eventType = varchar("event_type", 100)
    val payload = text("payload")
    val occurredAt = timestampWithTimeZone("occurred_at")

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(aggregateType, aggregateId, version)
    }
}
