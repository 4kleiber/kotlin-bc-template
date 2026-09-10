package com.akl.bctemplate.application.configuration

import com.akl.bctemplate.domain.Tracer
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.springframework.stereotype.Component

// Cross-cutting concern hooked in via the @DomainService marker rather than by touching domain
// code: every @DomainService method is wrapped in a span, without a single `import
// io.opentelemetry` ever appearing in the domain module. Requires domain/build.gradle.kts'
// `allOpen { annotation(...DomainService) }` so Spring can proxy these (final-by-default) classes.
@Aspect
@Component
class DomainServiceTracingAspect(private val tracer: Tracer) {

    @Around("@within(com.akl.bctemplate.domain.services.DomainService)")
    fun traceMethod(joinPoint: ProceedingJoinPoint): Any? {
        val className = joinPoint.target.javaClass.canonicalName
        val methodName = joinPoint.signature.name
        val spanName = "$className.$methodName"
        tracer.startSpan(spanName).use {
            return joinPoint.proceed()
        }
    }
}
