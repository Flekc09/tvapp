package com.tvapp.playback

import com.tvapp.core.FakeDateProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BrokenHereTest {
    @Test fun threeFailuresSameDayAreNotBroken() {
        val d = FakeDateProvider(LocalDate.of(2026, 9, 22)); val b = BrokenHere(d)
        var s = b.afterFailure(null, "ch"); s = b.afterFailure(s, "ch"); s = b.afterFailure(s, "ch")
        assertEquals("2026-09-22", s.failedDays); assertFalse(b.isBroken(s))
    }
    @Test fun failuresOnThreeSeparateDaysAreBroken() {
        val d = FakeDateProvider(LocalDate.of(2026, 9, 22)); val b = BrokenHere(d)
        var s = b.afterFailure(null, "ch"); d.nextDay(); s = b.afterFailure(s, "ch")
        assertFalse(b.isBroken(s)); d.nextDay(); s = b.afterFailure(s, "ch")
        assertTrue(b.isBroken(s)); assertEquals("broken_here", s.status)
    }
    @Test fun daysNeedNotBeConsecutive() {
        val d = FakeDateProvider(LocalDate.of(2026, 9, 1)); val b = BrokenHere(d)
        var s = b.afterFailure(null, "ch"); d.date = LocalDate.of(2026, 9, 10); s = b.afterFailure(s, "ch"); d.date = LocalDate.of(2026, 10, 3); s = b.afterFailure(s, "ch")
        assertTrue(b.isBroken(s))
    }
}
