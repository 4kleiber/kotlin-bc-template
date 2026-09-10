package com.akl.bctemplate.domain

import java.time.Instant
import java.util.UUID

// The common shape every bounded context's events share, so a single, system-wide events
// table (see storage's EventsTable) can store facts from every aggregate type in one
// strictly ordered append log. A bounded context defines its own sealed class extending this
// one (e.g. notes' NoteEvent) to keep exhaustive `when` matching over its own closed set of
// event types, while still sharing the one table underneath.
//
// Subclasses that want correct equals()/hashCode()/copy() must redeclare these three
// properties as `override val` in their own primary constructor — Kotlin's data class
// machinery only looks at a class's own primary-constructor properties, not inherited ones,
// so leaving them un-redeclared would silently drop them from equality.
abstract class DomainEvent(
    open val aggregateType: String,
    open val aggregateId: UUID,
    open val occurredAt: Instant,
)
