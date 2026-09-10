import org.gradle.api.flow.FlowAction
import org.gradle.api.flow.FlowParameters
import org.gradle.api.flow.FlowProviders
import org.gradle.api.flow.FlowScope
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestResult
import org.gradle.kotlin.dsl.newInstance
import javax.inject.Inject

plugins {
    kotlin("jvm") version "2.4.10" apply false
    kotlin("plugin.spring") version "2.4.10" apply false
    id("org.springframework.boot") version "4.1.1" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
}


group = "com.akl"
version = "0.0.1-SNAPSHOT"

data class TestSummary(val module: String, var total: Long = 0, var passed: Long = 0, var failed: Long = 0, var skipped: Long = 0)

val testSummaries = mutableListOf<TestSummary>()

subprojects {
    val moduleName = project.name
    tasks.withType<Test> {
        testLogging {
            events("skipped", "failed")
            showStandardStreams = false
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.SHORT
        }
        addTestListener(object : TestListener {
            override fun afterSuite(desc: TestDescriptor, result: TestResult) {
                if (desc.parent == null) {
                    testSummaries.add(TestSummary(
                        module = moduleName,
                        total = result.testCount,
                        passed = result.successfulTestCount,
                        failed = result.failedTestCount,
                        skipped = result.skippedTestCount
                    ))
                }
            }
        })
    }
}

fun testSummaryReport(): String {
    if (testSummaries.isEmpty()) return ""

    val totalTests = testSummaries.sumOf { it.total }
    val totalPassed = testSummaries.sumOf { it.passed }
    val totalFailed = testSummaries.sumOf { it.failed }
    val totalSkipped = testSummaries.sumOf { it.skipped }
    val result = if (totalFailed > 0) "FAILURE" else "SUCCESS"

    return """
        |
        |==========================================
        |Test Summary:
        |${testSummaries.joinToString("\n|") { "  ${it.module}: ${it.passed} passed, ${it.failed} failed, ${it.skipped} skipped (${it.total} tests)" }}
        |------------------------------------------
        |  Total:   $totalTests
        |  Passed:  $totalPassed
        |  Failed:  $totalFailed
        |  Skipped: $totalSkipped
        |  Result:  $result
        |==========================================
    """.trimMargin()
}

// `Gradle.buildFinished` is deprecated; the Flow API is the supported replacement for
// running code once the build has finished. `FlowScope`/`FlowProviders` aren't otherwise
// injectable at build-script scope, so they're obtained via a managed helper type.
abstract class FlowServices {
    @get:Inject
    abstract val flowScope: FlowScope

    @get:Inject
    abstract val flowProviders: FlowProviders
}

interface TestSummaryParameters : FlowParameters {
    @get:Input
    val summary: Property<String>
}

abstract class PrintTestSummaryAction : FlowAction<TestSummaryParameters> {
    override fun execute(parameters: TestSummaryParameters) {
        val summary = parameters.summary.get()
        if (summary.isNotEmpty()) {
            println(summary)
        }
    }
}

val flowServices = project.objects.newInstance<FlowServices>()

flowServices.flowScope.always(PrintTestSummaryAction::class.java) {
    parameters.summary.set(flowServices.flowProviders.buildWorkResult.map { testSummaryReport() })
}
