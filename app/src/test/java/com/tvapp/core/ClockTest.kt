package com.tvapp.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ClockTest {
    @Test fun fakeClockAdvances() {
        val c = FakeClock(100); c.advance(50); assertEquals(150L, c.elapsedMs())
    }
    @Test fun fakeDateNextDay() {
        val d = FakeDateProvider(LocalDate.of(2026, 9, 22)); d.nextDay(); assertEquals(LocalDate.of(2026, 9, 23), d.today())
    }
}
