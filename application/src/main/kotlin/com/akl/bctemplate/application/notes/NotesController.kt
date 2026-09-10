package com.akl.bctemplate.application.notes

import com.akl.bctemplate.application.notes.dto.CreateNoteRequest
import com.akl.bctemplate.application.notes.dto.NoteResponse
import com.akl.bctemplate.application.notes.dto.PageResponse
import com.akl.bctemplate.domain.PageRequest
import com.akl.bctemplate.domain.notes.Note
import com.akl.bctemplate.domain.notes.NoteService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

// Example bounded context's REST adapter. Note this never injects NoteRepository directly —
// only NoteService, per the layering rule in agent.md.
@RestController
@RequestMapping("/api/notes")
class NotesController(private val noteService: NoteService) {

    @PostMapping
    fun create(@RequestBody request: CreateNoteRequest): NoteResponse =
        noteService.createNote(request.title, request.body).toResponse()

    @GetMapping
    fun list(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): PageResponse {
        val result = noteService.listNotes(PageRequest(page, size))
        return PageResponse(
            content = result.content.map { it.toResponse() },
            totalElements = result.totalElements,
            totalPages = result.totalPages,
            page = result.page,
            size = result.size,
        )
    }

    @GetMapping("/{id}")
    fun get(@PathVariable id: UUID): NoteResponse =
        noteService.findNote(id)?.toResponse() ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)

    @PostMapping("/{id}/publish")
    fun publish(@PathVariable id: UUID): NoteResponse {
        noteService.findNote(id) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        return noteService.publishNote(id)?.toResponse()
            ?: throw ResponseStatusException(HttpStatus.CONFLICT, "Note cannot be published from its current status")
    }

    @PostMapping("/{id}/archive")
    fun archive(@PathVariable id: UUID): NoteResponse {
        noteService.findNote(id) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        return noteService.archiveNote(id)?.toResponse()
            ?: throw ResponseStatusException(HttpStatus.CONFLICT, "Note cannot be archived from its current status")
    }

    private fun Note.toResponse() = NoteResponse(
        id = id!!,
        title = title,
        body = body,
        status = status.name,
        createdAt = createdAt,
        publishedAt = publishedAt,
    )
}
