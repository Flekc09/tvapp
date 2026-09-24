package com.tvapp.playback

import com.tvapp.core.FakeClock
import com.tvapp.data.db.StreamStatEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MeasurementTest {
    private class FakeSink : StatSink { val rows = mutableListOf<StreamStatEntity>(); override suspend fun insert(s: StreamStatEntity) { rows += s } }

    @Test fun everyStreamGetsASampleAcrossFailovers() = runTest {
        val clock = FakeClock(); val sink = FakeSink()
        val m = Measurement(sink, clock, this)
        m.start("a"); clock.advance(800); m.firstFrame(); m.bitrate(3000); clock.advance(5000); m.rebuffer()
        m.start("b"); clock.advance(300); m.firstFrame(); clock.advance(1000)
        m.finish(); advanceUntilIdle()
        assertEquals(listOf("a", "b"), sink.rows.map { it.streamUrl })
        assertEquals(800, sink.rows[0].ttffMs); assertEquals(1, sink.rows[0].rebuffers); assertEquals(3000, sink.rows[0].bitrateKbps); assertEquals(5000L, sink.rows[0].playedMs)
        assertEquals(300, sink.rows[1].ttffMs); assertEquals(1000L, sink.rows[1].playedMs)
    }
    @Test fun aStreamThatNeverShowedAFrameIsNotSampled() = runTest {
        val sink = FakeSink(); val m = Measurement(sink, FakeClock(), this)
        m.start("dead"); m.finish(); advanceUntilIdle()
        assertEquals(0, sink.rows.size)
    }
}
