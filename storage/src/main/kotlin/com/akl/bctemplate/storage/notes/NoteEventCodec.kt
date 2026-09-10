package com.akl.bctemplate.storage.notes

import com.akl.bctemplate.domain.notes.NoteArchived
import com.akl.bctemplate.domain.notes.NoteCreated
import com.akl.bctemplate.domain.notes.NoteEvent
import com.akl.bctemplate.domain.notes.NotePublished
import com.fasterxml.jackson.annotation.JsonProperty
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import java.util.UUID

// The (de)serialization boundary between the domain's sealed NoteEvent hierarchy and the
// generic (event_type: text, payload: text/jsonb) shape notes_events actually stores — the
// only two columns needed to reconstruct any NoteEvent, however many subtypes it grows.
internal object NoteEventCodec {
    private val jsonMapper = JsonMapper.builder().build()

    // Events with no extra fields (NotePublished/NoteArchived) still get a payload — `{}` —
    // rather than a special-cased nullable column, keeping every row's shape uniform.
    // @JsonProperty on each constructor param is what lets plain jackson-databind (no Kotlin
    // module registered here, unlike application's autoconfigured ObjectMapper) construct this
    // Kotlin data class from JSON — without it, Jackson can't see the primary constructor as
    // a creator and throws InvalidDefinitionException.
    private data class NoteCreatedPayload(
        @JsonProperty("title") val title: String,
        @JsonProperty("body") val body: String,
    )

    fun encode(event: NoteEvent): Pair<String, String> = when (event) {
        is NoteCreated -> "NoteCreated" to jsonMapper.writeValueAsString(NoteCreatedPayload(event.title, event.body))
        is NotePublished -> "NotePublished" to "{}"
        is NoteArchived -> "NoteArchived" to "{}"
    }

    fun decode(eventType: String, aggregateId: UUID, occurredAt: Instant, payload: String): NoteEvent =
        when (eventType) {
            "NoteCreated" -> jsonMapper.readValue(payload, NoteCreatedPayload::class.java)
                .let { NoteCreated(aggregateId, it.title, it.body, occurredAt) }
            "NotePublished" -> NotePublished(aggregateId, occurredAt)
            "NoteArchived" -> NoteArchived(aggregateId, occurredAt)
            else -> error("Unknown note event type: $eventType")
        }
}
