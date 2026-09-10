package com.akl.bctemplate.application.eventstore

import com.akl.bctemplate.application.AbstractIntegrationTest
import com.akl.bctemplate.domain.ConcurrentEventAppendException
import com.akl.bctemplate.domain.DomainEvent
import com.akl.bctemplate.domain.EventStore
import com.akl.bctemplate.domain.PageRequest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

// Exercises EventStore directly and generically, against the real Postgres-backed adapter —
// deliberately using a stream_type that isn't "Note", to prove the store has no
// bounded-context-specific code path (see ExposedEventStore's doc).
class EventStoreIntegrationTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var eventStore: EventStore

    private val streamType = STREAM_TYPE

    // A stand-in for a bounded context's own event class (e.g. notes.NoteCreated) — DomainEvent
    // is abstract, so a concrete subclass is needed to construct one directly, exactly like a
    // real bounded context would. Nested (not inner) so it can't accidentally reach the outer
    // test instance — it references the companion's constant instead of the outer `streamType`.
    private data class WidgetEvent(
        override val streamId: UUID,
        override val eventType: String,
        val fields: Map<String, Any?>,
        override val occurredAt: Instant,
    ) : DomainEvent(streamId, STREAM_TYPE, eventType, occurredAt) {
        override fun data(): Map<String, Any?> = fields
    }

    companion object {
        private const val STREAM_TYPE = "IntegrationTestWidget"
    }

    @Test
    fun `append then loadEvents replays a stream's events in version order`() {
        val tenantId = UUID.randomUUID()
        val streamId = UUID.randomUUID()
        val created = WidgetEvent(streamId, "Created", mapOf("name" to "Widget"), Instant.now())
        val renamed = WidgetEvent(streamId, "Renamed", mapOf("name" to "New name"), Instant.now())

        eventStore.append(tenantId, streamId, streamType, 0, listOf(created))
        eventStore.append(tenantId, streamId, streamType, 1, listOf(renamed))

        val events = eventStore.loadEvents(tenantId, streamId, streamType)
        assertEquals(listOf("Created", "Renamed"), events.map { it.eventType })
        assertEquals(listOf(1L, 2L), events.map { it.version })
        assertEquals(mapOf("name" to "New name"), events[1].eventData)
    }

    @Test
    fun `append rejects a stale expected version`() {
        val tenantId = UUID.randomUUID()
        val streamId = UUID.randomUUID()
        eventStore.append(tenantId, streamId, streamType, 0, listOf(WidgetEvent(streamId, "Created", emptyMap(), Instant.now())))

        assertFailsWith<ConcurrentEventAppendException> {
            eventStore.append(tenantId, streamId, streamType, 0, listOf(WidgetEvent(streamId, "Renamed", emptyMap(), Instant.now())))
        }
    }

    @Test
    fun `loadEvents returns nothing for an unknown stream`() {
        assertEquals(emptyList(), eventStore.loadEvents(UUID.randomUUID(), UUID.randomUUID(), streamType))
    }

    @Test
    fun `listStreamIds finds every stream of a type for the tenant`() {
        val tenantId = UUID.randomUUID()
        val streamA = UUID.randomUUID()
        val streamB = UUID.randomUUID()
        eventStore.append(tenantId, streamA, streamType, 0, listOf(WidgetEvent(streamA, "Created", emptyMap(), Instant.now())))
        eventStore.append(tenantId, streamB, streamType, 0, listOf(WidgetEvent(streamB, "Created", emptyMap(), Instant.now())))

        val page = eventStore.listStreamIds(tenantId, streamType, PageRequest(page = 0, size = 50))

        assertTrue(page.content.containsAll(listOf(streamA, streamB)))
    }

    @Test
    fun `listStreamIds does not see another tenant's streams`() {
        val tenantId = UUID.randomUUID()
        val otherTenantId = UUID.randomUUID()
        val streamId = UUID.randomUUID()
        eventStore.append(otherTenantId, streamId, streamType, 0, listOf(WidgetEvent(streamId, "Created", emptyMap(), Instant.now())))

        val page = eventStore.listStreamIds(tenantId, streamType, PageRequest(page = 0, size = 50))

        assertTrue(streamId !in page.content)
    }
}
