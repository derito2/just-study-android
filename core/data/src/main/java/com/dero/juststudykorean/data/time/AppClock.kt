package com.dero.juststudykorean.data.time

import java.time.Instant
import java.time.ZoneId

/** The only source of "now" and of the phone's zone (instants in UTC, days in the phone's zone). */
interface AppClock {
    fun now(): Instant
    val zone: ZoneId
}

class SystemClock : AppClock {
    override fun now(): Instant = Instant.now()
    override val zone: ZoneId get() = ZoneId.systemDefault()
}

/** Tests: a fixed instant and zone; [now] can be moved. */
class FixedClock(var instant: Instant, override val zone: ZoneId) : AppClock {
    override fun now(): Instant = instant
}
