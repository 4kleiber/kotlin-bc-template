package com.akl.bctemplate.application

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

// scanBasePackages covers com.akl.bctemplate.storage too: storage's @Repository beans live
// outside this class's own package, and Spring Boot's component scan only covers subpackages
// of the @SpringBootApplication class by default.
@SpringBootApplication(scanBasePackages = ["com.akl.bctemplate"])
class BcTemplateApplication

fun main(args: Array<String>) {
    runApplication<BcTemplateApplication>(*args)
}
