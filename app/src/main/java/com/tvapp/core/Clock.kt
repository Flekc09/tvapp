package com.tvapp.core

import android.os.SystemClock
import java.time.LocalDate

interface Clock { fun elapsedMs(): Long }
interface DateProvider { fun today(): LocalDate }

class SystemClockImpl : Clock { override fun elapsedMs() = SystemClock.elapsedRealtime() }
class SystemDateProvider : DateProvider { override fun today(): LocalDate = LocalDate.now() }

class FakeClock(var now: Long = 0L) : Clock {
    override fun elapsedMs() = now
    fun advance(ms: Long) { now += ms }
}
class FakeDateProvider(var date: LocalDate = LocalDate.of(2026, 9, 22)) : DateProvider {
    override fun today() = date
    fun nextDay() { date = date.plusDays(1) }
}
