package com.akl.bctemplate.application.notes

import com.akl.bctemplate.domain.notes.Note
import com.akl.bctemplate.domain.notes.NoteStatus
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.FormMethod
import kotlinx.html.InputType
import kotlinx.html.TBODY
import kotlinx.html.body
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.head
import kotlinx.html.html
import kotlinx.html.input
import kotlinx.html.meta
import kotlinx.html.stream.createHTML
import kotlinx.html.style
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.title
import kotlinx.html.tr
import kotlinx.html.unsafe

// UI convention (see agent.md Tech Stack): views are extension functions on FlowContent (or,
// for the page shell, HTML) — never Thymeleaf/JSP templates. Plain form posts + a full page
// reload; no htmx here, kept minimal since the point is the convention, not the interactivity.
// Styling is a small hand-written stylesheet inlined below — no CSS framework dependency.
fun renderNotesPage(notes: List<Note>): String = createHTML().html {
    head {
        meta(charset = "UTF-8")
        meta(name = "viewport", content = "width=device-width, initial-scale=1.0")
        title { +"Notes" }
        style {
            unsafe {
                raw(NOTES_PAGE_CSS)
            }
        }
    }
    body {
        div(classes = "page") {
            h1 { +"Notes" }
            noteForm()
            noteTable(notes)
        }
    }
}

private val NOTES_PAGE_CSS =
    """
    body { font-family: system-ui, sans-serif; margin: 0; padding: 2rem; color: #1a1a1a; }
    .page { max-width: 48rem; margin: 0 auto; }
    .note-form { display: flex; gap: 0.5rem; margin-bottom: 1.5rem; }
    .note-form input[type=text] { flex: 1; padding: 0.4rem; }
    table { border-collapse: collapse; width: 100%; }
    th, td { text-align: left; padding: 0.5rem; border-bottom: 1px solid #ddd; }
    .note-actions form { display: inline; margin-right: 0.5rem; }
    button { padding: 0.3rem 0.6rem; cursor: pointer; }
    """.trimIndent()

private fun FlowContent.noteForm() {
    form(action = "/notes", method = FormMethod.post, classes = "note-form") {
        input(type = InputType.text, name = "title") {
            placeholder = "Title"
            required = true
        }
        input(type = InputType.text, name = "body") {
            placeholder = "Body"
            required = true
        }
        button(type = ButtonType.submit) { +"Add note" }
    }
}

private fun FlowContent.noteTable(notes: List<Note>) {
    table {
        thead {
            tr {
                th { +"Title" }
                th { +"Status" }
                th { +"Actions" }
            }
        }
        tbody {
            notes.forEach { note -> noteRow(note) }
        }
    }
}

private fun TBODY.noteRow(note: Note) {
    tr {
        td { +note.title }
        td { +note.status.name }
        td(classes = "note-actions") {
            if (note.status == NoteStatus.DRAFT) {
                form(action = "/notes/${note.id}/publish", method = FormMethod.post) {
                    button(type = ButtonType.submit) { +"Publish" }
                }
            }
            if (note.status != NoteStatus.ARCHIVED) {
                form(action = "/notes/${note.id}/archive", method = FormMethod.post) {
                    button(type = ButtonType.submit) { +"Archive" }
                }
            }
        }
    }
}
