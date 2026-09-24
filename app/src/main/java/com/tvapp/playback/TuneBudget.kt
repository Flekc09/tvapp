package com.tvapp.playback

import com.tvapp.core.Clock

class TuneBudget(private val clock: Clock, private val totalMs: Long = 10_000, private val perStreamMs: Long = 4_000) {
    private var startedAt = 0L
    fun start() { startedAt = clock.elapsedMs() }
    fun remainingMs(): Long = (totalMs - (clock.elapsedMs() - startedAt)).coerceAtLeast(0)
    fun cutoffMs(streamsRemainingAfterThis: Int): Long =
        if (streamsRemainingAfterThis > 0) minOf(perStreamMs, remainingMs()) else remainingMs()
    fun exhausted() = remainingMs() <= 0
}
