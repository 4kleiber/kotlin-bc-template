package com.akl.bctemplate.domain

// Ports for the cross-cutting tracing aspect (see application's DomainServiceTracingAspect).
// Kept in domain, framework-free, so @DomainService classes never import an OTel type directly.
interface SpanScope : AutoCloseable

interface Tracer {
    fun startSpan(name: String): SpanScope
}
