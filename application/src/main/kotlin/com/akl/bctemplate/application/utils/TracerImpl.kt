package com.akl.bctemplate.application.utils

import com.akl.bctemplate.domain.SpanScope
import com.akl.bctemplate.domain.Tracer
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationRegistry
import org.springframework.stereotype.Component

@Component
class TracerImpl(
    private val registry: ObservationRegistry,
) : Tracer {
    override fun startSpan(name: String): SpanScope {
        val observation = Observation.start(name, registry)
        return object : SpanScope {
            override fun close() {
                observation.stop()
            }
        }
    }
}
