package com.akl.bctemplate.application.notes

import com.akl.bctemplate.application.AbstractIntegrationTest
import com.akl.bctemplate.domain.notes.ConcurrentEventAppendException
import com.akl.bctemplate.domain.notes.NoteArchived
import com.akl.bctemplate.domain.notes.NoteCreated
import com.akl.bctemplate.domain.notes.NoteEventStore
import com.akl.bctemplate.domain.notes.NotePublished
import com.akl.bctemplate.domain.notes.NoteRepository
import com.akl.bctemplate.domain.notes.NoteStatus
import com.akl.bctemplate.storage.notes.NoteProjectionRebuilder
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

// Exercises the storage-layer mechanics that NotesControllerIntegrationTest never touches
// directly: the two beans wired to the same tables, the optimistic-concurrency guard, and
// projection rebuildability.
class NoteEventStoreIntegrationTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var eventStore: NoteEventStore

    @Autowired
    private lateinit var noteRepository: NoteRepository

    @Autowired
    private lateinit var projectionRebuilder: NoteProjectionRebuilder

    @Test
    fun `appending events keeps the projection in sync`() {
        val id = UUID.randomUUID()

        eventStore.append(id, 0, listOf(NoteCreated(id, "Groceries", "Milk, eggs, bread", Instant.now())))
        eventStore.append(id, 1, listOf(NotePublished(id, Instant.now())))

        val projected = noteRepository.findById(id)
        assertNotNull(projected)
        assertEquals(NoteStatus.PUBLISHED, projected.status)
    }

    @Test
    fun `loadEvents replays the full ordered event history`() {
        val id = UUID.randomUUID()
        // Postgres' timestamptz rounds to microseconds, so occurredAt must be pre-truncated
        // here for the round-tripped event to compare equal to what we appended.
        val created = NoteCreated(id, "Groceries", "Milk, eggs, bread", Instant.now().truncatedTo(ChronoUnit.MICROS))
        val archived = NoteArchived(id, Instant.now().truncatedTo(ChronoUnit.MICROS))
        eventStore.append(id, 0, listOf(created))
        eventStore.append(id, 1, listOf(archived))

        assertEquals(listOf(created, archived), eventStore.loadEvents(id))
    }

    @Test
    fun `append rejects a stale expected version`() {
        val id = UUID.randomUUID()
        eventStore.append(id, 0, listOf(NoteCreated(id, "Groceries", "Milk, eggs, bread", Instant.now())))

        assertFailsWith<ConcurrentEventAppendException> {
            eventStore.append(id, 0, listOf(NotePublished(id, Instant.now())))
        }
    }

    @Test
    fun `loadEvents returns nothing for an unknown aggregate`() {
        assertEquals(emptyList(), eventStore.loadEvents(UUID.randomUUID()))
        assertNull(noteRepository.findById(UUID.randomUUID()))
    }

    @Test
    fun `the projection can be rebuilt from the event log alone`() {
        val id = UUID.randomUUID()
        eventStore.append(id, 0, listOf(NoteCreated(id, "Groceries", "Milk, eggs, bread", Instant.now())))
        eventStore.append(id, 1, listOf(NotePublished(id, Instant.now())))
        val beforeRebuild = noteRepository.findById(id)

        projectionRebuilder.rebuildAll()

        assertEquals(beforeRebuild, noteRepository.findById(id))
    }
}
