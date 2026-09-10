package com.akl.bctemplate.application.notes

import com.akl.bctemplate.application.AbstractIntegrationTest
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.ObjectMapper
import java.util.UUID

class NotesControllerIntegrationTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private fun createNote(title: String = "Groceries ${UUID.randomUUID()}", body: String = "Milk, eggs, bread"): String {
        val response = mockMvc.perform(
            post("/api/notes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"title":"$title","body":"$body"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("DRAFT"))
            .andReturn().response.contentAsString
        return objectMapper.readTree(response)["id"].asString()
    }

    @Test
    fun `POST creates a draft note`() {
        mockMvc.perform(
            post("/api/notes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"title":"Groceries","body":"Milk, eggs, bread"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.title").value("Groceries"))
            .andExpect(jsonPath("$.status").value("DRAFT"))
    }

    @Test
    fun `GET by id returns the created note`() {
        val id = createNote(title = "Findable note")

        mockMvc.perform(get("/api/notes/$id"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.title").value("Findable note"))
    }

    @Test
    fun `GET by id returns 404 when the note does not exist`() {
        mockMvc.perform(get("/api/notes/${UUID.randomUUID()}"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `POST publish transitions a draft note to published`() {
        val id = createNote()

        mockMvc.perform(post("/api/notes/$id/publish"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("PUBLISHED"))
    }

    @Test
    fun `POST publish twice returns a conflict the second time`() {
        val id = createNote()
        mockMvc.perform(post("/api/notes/$id/publish")).andExpect(status().isOk)

        mockMvc.perform(post("/api/notes/$id/publish"))
            .andExpect(status().isConflict)
    }

    @Test
    fun `POST archive transitions a note to archived`() {
        val id = createNote()

        mockMvc.perform(post("/api/notes/$id/archive"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("ARCHIVED"))
    }

    @Test
    fun `GET list includes previously created notes`() {
        val title = "Listed note ${UUID.randomUUID()}"
        createNote(title = title)

        mockMvc.perform(get("/api/notes").param("page", "0").param("size", "50"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[*].title", org.hamcrest.Matchers.hasItem(title)))
    }

    @Test
    fun `GET notes page renders the kotlinx html view`() {
        val title = "UI note ${UUID.randomUUID()}"
        createNote(title = title)

        mockMvc.perform(get("/notes").accept(MediaType.TEXT_HTML))
            .andExpect(status().isOk)
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(containsString(title)))
    }
}
