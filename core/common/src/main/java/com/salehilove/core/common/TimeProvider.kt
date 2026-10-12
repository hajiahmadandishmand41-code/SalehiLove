package com.salehilove.core.common

import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Injectable clock so that all scheduling / SRS logic is testable on the JVM.
 */
interface TimeProvider {
    fun now(): LocalDateTime
    fun today(): LocalDate = now().toLocalDate()
}

class SystemTimeProvider(private val clock: Clock = Clock.systemDefaultZone()) : TimeProvider {
    override fun now(): LocalDateTime = LocalDateTime.now(clock)
}

/** Deterministic clock for tests. */
class FixedTimeProvider(private val fixed: LocalDateTime) : TimeProvider {
    override fun now(): LocalDateTime = fixed
}
