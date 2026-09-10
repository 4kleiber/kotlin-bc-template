package com.akl.bctemplate.storage.eventstore

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.jetbrains.exposed.v1.json.jsonb
import tools.jackson.databind.json.JsonMapper

private val jsonMapper: JsonMapper = JsonMapper.builder().build()

@Suppress("UNCHECKED_CAST")
private fun encodeJson(value: Map<String, Any?>): String = jsonMapper.writeValueAsString(value)

@Suppress("UNCHECKED_CAST")
private fun decodeJson(json: String): Map<String, Any?> = jsonMapper.readValue(json, Map::class.java) as Map<String, Any?>

// The single, system-wide event log every bounded context appends to (see EventStore /
// ExposedEventStore) — column names and types mirror the physical Postgres schema exactly
// (see V1__CreateEventStore.sql). `event_data`/`metadata` are the one place in the whole
// codebase that turns a Kotlin Map into/out of actual JSON text; everywhere above this file
// deals only in Map<String, Any?>.
internal object EventsTable : Table("events") {
    val id = javaUUID("id")
    val tenantId = javaUUID("tenant_id")
    val streamId = javaUUID("stream_id")
    val streamType = varchar("stream_type", 255)
    val version = long("version")
    val eventType = varchar("event_type", 255)
    val eventData = jsonb<Map<String, Any?>>("event_data", ::encodeJson, ::decodeJson)
    val metadata = jsonb<Map<String, Any?>>("metadata", ::encodeJson, ::decodeJson).nullable()
    val createdAt = timestampWithTimeZone("created_at")

    // A separate, purely-additive counter — never used to identify a row, only to order
    // them — giving the exact global order every event was ever created in, across every
    // tenant and stream. Postgres assigns it (BIGSERIAL); the application never sets it.
    val sequenceNumber = long("sequence_number").autoIncrement()

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(tenantId, streamId, version)
    }
}
