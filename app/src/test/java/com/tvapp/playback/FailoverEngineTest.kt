package com.tvapp.playback

import com.tvapp.core.FakeClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FailoverEngineTest {
    private fun c(u: String) = Candidate(u, "up", 0.5, null, "hls", null, null)
    private fun engine(clock: FakeClock) = FailoverEngine(clock) { TuneBudget(clock) }

    @Test fun firstStreamPlaysWithTryingNoticeAndFirstFrameSettles() {
        val clock = FakeClock(); val e = engine(clock)
        assertEquals(Decision.Play(c("a"), Notice.Trying(1, 2)), e.begin(listOf(c("a"), c("b"))))
        assertEquals(Decision.Continue, e.onEvent(PlayerEvent.FirstFrame))
        assertEquals("a", e.playing!!.url)
    }
    @Test fun singleStreamChannelHasNoTryingNotice() {
        val clock = FakeClock(); val e = engine(clock)
        assertEquals(Decision.Play(c("only"), null), e.begin(listOf(c("only"))))
    }
    @Test fun emptyQueueStops() {
        assertEquals(Decision.Stop(Notice.NotWorking(0)), engine(FakeClock()).begin(emptyList()))
    }
    @Test fun errorBeforeFirstFrameMovesToNextWithPositionNotice() {
        val clock = FakeClock(); val e = engine(clock); e.begin(listOf(c("a"), c("b"), c("c")))
        assertEquals(Decision.Play(c("b"), Notice.Trying(2, 3)), e.onEvent(PlayerEvent.Error("boom")))
        assertEquals(setOf("a"), e.failedThisTune)
    }
    @Test fun cutoffMarksSlowNotFailed() {
        val clock = FakeClock(); val e = engine(clock); e.begin(listOf(c("a"), c("b")))
        clock.advance(4001)
        assertEquals(Decision.Play(c("b"), Notice.Trying(2, 2)), e.onTick())
        assertEquals(setOf("a"), e.slowThisTune); assertTrue(e.failedThisTune.isEmpty())
    }
    @Test fun singleStreamGetsWholeBudgetThenNotWorking() {
        val clock = FakeClock(); val e = engine(clock); e.begin(listOf(c("only")))
        clock.advance(4001); assertEquals(Decision.Continue, e.onTick())
        clock.advance(6000); assertEquals(Decision.Stop(Notice.NotWorking(1)), e.onTick())
    }
    @Test fun allStreamsErroringBeforeFirstFrameStopsImmediatelyWithoutWaitingForBudget() {
        val clock = FakeClock(); val e = engine(clock); e.begin(listOf(c("a"), c("b")))
        e.onEvent(PlayerEvent.Error("x"))
        assertEquals(Decision.Stop(Notice.NotWorking(2)), e.onEvent(PlayerEvent.Error("x")))
        assertEquals(Decision.Continue, e.onTick()) // inactive after stop
    }
    @Test fun budgetExhaustedWhileSlowStreamsRemainStops() {
        val clock = FakeClock(); val e = engine(clock); e.begin(listOf(c("a"), c("b"), c("c")))
        clock.advance(4001); e.onTick(); clock.advance(4001); e.onTick(); clock.advance(2000)
        assertEquals(Decision.Stop(Notice.NotWorking(3)), e.onTick())
    }
    @Test fun midPlayDeathAfterLongPlaybackStartsAFreshTuneAndDoesNotRetryTheDeadStreamFirst() {
        val clock = FakeClock(); val e = engine(clock); e.begin(listOf(c("a"), c("b")))
        e.onEvent(PlayerEvent.FirstFrame)
        clock.advance(30 * 60_000) // half an hour of playback, far past the 10 s budget
        assertEquals(Decision.Play(c("b"), Notice.Switching), e.onEvent(PlayerEvent.Error("died")))
        assertEquals("a", e.lastFailedUrl)
        assertTrue(e.failedThisTune.isEmpty()) // fresh tune
        clock.advance(4001)
        assertEquals(Decision.Continue, e.onTick()) // b is the last untried stream, so it gets the remaining budget, not 4 s
        clock.advance(6000)
        assertEquals(Decision.Stop(Notice.NotWorking(2)), e.onTick())
    }
    @Test fun aStreamThatJustDiedIsRetriedOnlyAfterATwoSecondGap() {
        val clock = FakeClock(); val e = engine(clock); e.begin(listOf(c("a"), c("b")))
        e.onEvent(PlayerEvent.FirstFrame); clock.advance(10_000)
        assertEquals(Decision.Play(c("b"), Notice.Switching), e.onEvent(PlayerEvent.Error("a died")))
        e.onEvent(PlayerEvent.FirstFrame); clock.advance(500)
        // b dies 500 ms after a did. a is the only other stream; it died 500 ms ago, so wait the rest of the gap.
        assertEquals(Decision.Retry(c("a"), 1500, Notice.Switching), e.onEvent(PlayerEvent.Error("b died")))
        e.onEvent(PlayerEvent.FirstFrame); clock.advance(100)
        assertEquals(Decision.Retry(c("b"), 1900, Notice.Switching), e.onEvent(PlayerEvent.Error("a died again")))
    }
    @Test fun streamsThatNeverProducedAFrameAreNotRetriedInTheSameTuneButWorkedOnesAre() {
        val clock = FakeClock(); val e = engine(clock); e.begin(listOf(c("a"), c("b")))
        assertEquals(Decision.Play(c("b"), Notice.Trying(2, 2)), e.onEvent(PlayerEvent.Error("a bad")))
        e.onEvent(PlayerEvent.FirstFrame); clock.advance(5000)
        assertEquals(Decision.Play(c("a"), Notice.Switching), e.onEvent(PlayerEvent.Error("b died"))) // new tune: a is untried again
        // a fails again before a frame. b worked and died 0 ms ago: retry it after the gap. a is not retried in this tune.
        assertEquals(Decision.Retry(c("b"), 2000, Notice.Trying(2, 2)), e.onEvent(PlayerEvent.Error("a bad again")))
        // b now fails before a frame too: nothing left, stop immediately.
        assertEquals(Decision.Stop(Notice.NotWorking(2)), e.onEvent(PlayerEvent.Error("b bad")))
    }
    @Test fun aStreamThatEndedIsNeverPickedAgainForTheChannel() {
        // An #EXT-X-ENDLIST playlist plays to its end and stops: it is not live, so no later tune may pick it (Opus adversarial review 2026-09-23, major 10).
        val clock = FakeClock(); val e = engine(clock); e.begin(listOf(c("a"), c("b")))
        e.onEvent(PlayerEvent.FirstFrame); clock.advance(5000)
        assertEquals(Decision.Play(c("b"), Notice.Switching), e.onEvent(PlayerEvent.Ended))
        e.onEvent(PlayerEvent.FirstFrame); clock.advance(5000)
        assertEquals(Decision.Retry(c("b"), 2000, Notice.Switching), e.onEvent(PlayerEvent.Error("b died"))) // a is not untried again
    }
    @Test fun aFourthMidPlayDeathWithinTwoMinutesStops() {
        val clock = FakeClock(); val e = engine(clock); e.begin(listOf(c("a"), c("b")))
        e.onEvent(PlayerEvent.FirstFrame); clock.advance(5000)
        assertEquals(Decision.Play(c("b"), Notice.Switching), e.onEvent(PlayerEvent.Error("1")))
        e.onEvent(PlayerEvent.FirstFrame); clock.advance(5000)
        assertEquals(Decision.Play(c("a"), Notice.Switching), e.onEvent(PlayerEvent.Error("2")))
        e.onEvent(PlayerEvent.FirstFrame); clock.advance(5000)
        assertEquals(Decision.Play(c("b"), Notice.Switching), e.onEvent(PlayerEvent.Error("3")))
        e.onEvent(PlayerEvent.FirstFrame); clock.advance(5000)
        assertEquals(Decision.Stop(Notice.NotWorking(2)), e.onEvent(PlayerEvent.Error("4")))
        assertNull(e.attempting)
    }
    @Test fun midPlayDeathsSpreadOverMoreThanTwoMinutesKeepSwitching() {
        val clock = FakeClock(); val e = engine(clock); e.begin(listOf(c("a"), c("b")))
        repeat(3) { e.onEvent(PlayerEvent.FirstFrame); clock.advance(60_000); e.onEvent(PlayerEvent.Error("died")) }
        e.onEvent(PlayerEvent.FirstFrame); clock.advance(60_000)
        assertEquals(Decision.Play(c("a"), Notice.Switching), e.onEvent(PlayerEvent.Error("died again"))) // only 2 deaths inside the last 2 minutes
    }
    @Test fun manualPickIsNotCutOffAtFourSeconds() {
        val clock = FakeClock(); val e = engine(clock); e.begin(listOf(c("m"), c("b")), manual = true)
        clock.advance(4001); assertEquals(Decision.Continue, e.onTick())
        clock.advance(6000); assertEquals(Decision.Stop(Notice.NotWorking(1)), e.onTick())
    }
    @Test fun manualPickFailureResumesTheNormalQueue() {
        val clock = FakeClock(); val e = engine(clock); e.begin(listOf(c("m"), c("b")), manual = true)
        assertEquals(Decision.Play(c("b"), Notice.Trying(2, 2)), e.onEvent(PlayerEvent.Error("x")))
    }
    @Test fun cancelStopsEverythingAndRecordsNothing() {
        val clock = FakeClock(); val e = engine(clock); e.begin(listOf(c("a"))); e.cancel()
        assertEquals(Decision.Continue, e.onEvent(PlayerEvent.Error("late")))
        assertNull(e.playing); assertNull(e.attempting); assertTrue(e.failedThisTune.isEmpty())
    }
}
