package com.akl.bctemplate.application

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.ApplicationContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.postgresql.PostgreSQLContainer

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractIntegrationTest {

    private val logger = LoggerFactory.getLogger(AbstractIntegrationTest::class.java)

    @Autowired
    protected lateinit var applicationContext: ApplicationContext

    @BeforeAll
    fun logContextId() {
        logger.info(">>> Spring ApplicationContext ID: ${applicationContext.id} <<<")
    }

    companion object {
        private val postgres = PostgreSQLContainer(
            "postgres:${System.getProperty("postgres.version") ?: error("postgres.version system property not set — check application/build.gradle.kts")}",
        )
            .waitingFor(Wait.forListeningPort())
            .withReuse(true)

        init {
            postgres.start()
        }

        @JvmStatic
        @DynamicPropertySource
        fun datasourceProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { "${postgres.jdbcUrl}&currentSchema=bctemplate" }
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }
}
