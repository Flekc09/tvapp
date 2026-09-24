package com.tvapp.playback

import com.tvapp.core.FakeClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TuneBudgetTest {
    @Test fun perStreamCutoffWhenAlternativesRemain() {
        val c = FakeClock(); val b = TuneBudget(c); b.start()
        assertEquals(4000L, b.cutoffMs(streamsRemainingAfterThis = 3))
    }
    @Test fun lastStreamGetsRemainingBudget() {
        val c = FakeClock(); val b = TuneBudget(c); b.start(); c.advance(3000)
        assertEquals(7000L, b.cutoffMs(streamsRemainingAfterThis = 0))
    }
    @Test fun singleStreamChannelGetsFullBudget() {
        val c = FakeClock(); val b = TuneBudget(c); b.start()
        assertEquals(10_000L, b.cutoffMs(streamsRemainingAfterThis = 0))
    }
    @Test fun perStreamCutoffShrinksNearTheEnd() {
        val c = FakeClock(); val b = TuneBudget(c); b.start(); c.advance(8500)
        assertEquals(1500L, b.cutoffMs(streamsRemainingAfterThis = 2))
    }
    @Test fun exhausted() {
        val c = FakeClock(); val b = TuneBudget(c); b.start(); c.advance(10_000); assertTrue(b.exhausted())
    }
}
