package com.akl.bctemplate.application.configuration

import com.akl.bctemplate.domain.services.DomainService
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.FilterType

// The wiring seam between the framework-free domain module and Spring: every class annotated
// @DomainService is picked up here and registered as a bean by classpath scanning + annotation
// filter, so adding a new bounded context's service requires no edit to this file at all.
@Configuration
@ComponentScan(
    basePackages = ["com.akl.bctemplate.domain"],
    includeFilters = [ComponentScan.Filter(type = FilterType.ANNOTATION, classes = [DomainService::class])],
)
class DomainConfiguration
