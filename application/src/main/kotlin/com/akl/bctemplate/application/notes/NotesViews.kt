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
import kotlinx.html.link
import kotlinx.html.meta
import kotlinx.html.stream.createHTML
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.title
import kotlinx.html.tr

// UI convention (see agent.md Tech Stack): views are extension functions on FlowContent (or,
// for the page shell, HTML) — never Thymeleaf/JSP templates. Plain form posts + a full page
// reload; no htmx here, kept minimal since the point is the convention, not the interactivity.
fun renderNotesPage(notes: List<Note>): String = createHTML().html {
    head {
        meta(charset = "UTF-8")
        meta(name = "viewport", content = "width=device-width, initial-scale=1.0")
        title { +"Notes" }
        link(rel = "stylesheet", href = "https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css")
    }
    body {
        div(classes = "container py-4") {
            h1 { +"Notes" }
            noteForm()
            noteTable(notes)
        }
    }
}

private fun FlowContent.noteForm() {
    form(action = "/notes", method = FormMethod.post, classes = "row g-2 mb-4") {
        div(classes = "col-md-4") {
            input(type = InputType.text, name = "title", classes = "form-control") {
                placeholder = "Title"
                required = true
            }
        }
        div(classes = "col-md-6") {
            input(type = InputType.text, name = "body", classes = "form-control") {
                placeholder = "Body"
                required = true
            }
        }
        div(classes = "col-md-2") {
            button(type = ButtonType.submit, classes = "btn btn-primary w-100") { +"Add note" }
        }
    }
}

private fun FlowContent.noteTable(notes: List<Note>) {
    table(classes = "table") {
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
        td {
            if (note.status == NoteStatus.DRAFT) {
                form(action = "/notes/${note.id}/publish", method = FormMethod.post) {
                    button(type = ButtonType.submit, classes = "btn btn-sm btn-outline-success me-1") { +"Publish" }
                }
            }
            if (note.status != NoteStatus.ARCHIVED) {
                form(action = "/notes/${note.id}/archive", method = FormMethod.post) {
                    button(type = ButtonType.submit, classes = "btn btn-sm btn-outline-secondary") { +"Archive" }
                }
            }
        }
    }
}
