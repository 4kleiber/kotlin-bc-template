package com.akl.bctemplate.application.notes

import com.akl.bctemplate.domain.PageRequest
import com.akl.bctemplate.domain.notes.NoteService
import org.springframework.http.MediaType
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.servlet.view.RedirectView
import java.util.UUID

// Minimal server-rendered page for the notes example, demonstrating the kotlinx.html
// convention alongside NotesController's JSON API — a bounded context can expose either,
// both, or neither.
@Controller
@RequestMapping("/notes")
class NotesUiController(private val noteService: NoteService) {

    @GetMapping(produces = [MediaType.TEXT_HTML_VALUE])
    @ResponseBody
    fun page(): String = renderNotesPage(noteService.listNotes(PageRequest(page = 0, size = 50)).content)

    @PostMapping
    fun create(@RequestParam title: String, @RequestParam body: String): RedirectView {
        noteService.createNote(title, body)
        return RedirectView("/notes")
    }

    @PostMapping("/{id}/publish")
    fun publish(@PathVariable id: UUID): RedirectView {
        noteService.publishNote(id)
        return RedirectView("/notes")
    }

    @PostMapping("/{id}/archive")
    fun archive(@PathVariable id: UUID): RedirectView {
        noteService.archiveNote(id)
        return RedirectView("/notes")
    }
}
