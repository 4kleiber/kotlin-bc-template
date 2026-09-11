package com.akl.bctemplate.application.notes.dto

import java.time.Instant
import java.util.UUID

data class CreateNoteRequest(val title: String, val body: String)

data class NoteResponse(
    val id: UUID,
    val title: String,
    val body: String,
    val status: String,
    val createdAt: Instant?,
    val publishedAt: Instant?,
)

data class PageResponse(
    val content: List<NoteResponse>,
    val totalElements: Long,
    val totalPages: Int,
    val page: Int,
    val size: Int,
)
