package com.tvapp.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamQueueTest {
    private fun c(url: String, health: String = "up", score: Double = 0.5, q: String? = "720p") = Candidate(url, health, score, q, "hls", null, null)

    @Test fun demotedLastThenNonUpThenScore() {
        val out = StreamQueue.order(listOf(c("a", score = 0.9), c("b", "unverified", 0.99), c("c", score = 0.7), c("d", "down", 0.1)), demotedUrls = setOf("a"), localScores = emptyMap())
        assertEquals(listOf("c", "b", "d", "a"), out.map { it.url })
    }
    @Test fun localScoreOverridesCatalogScore() {
        val out = StreamQueue.order(listOf(c("a", score = 0.9), c("b", score = 0.5)), emptySet(), mapOf("b" to 0.95))
        assertEquals(listOf("b", "a"), out.map { it.url })
    }
    @Test fun allDemotedKeepsRelativeOrder() {
        val out = StreamQueue.order(listOf(c("a", score = 0.3), c("b", score = 0.8)), setOf("a", "b"), emptyMap())
        assertEquals(listOf("b", "a"), out.map { it.url })
    }
    @Test fun singleStreamIsReturnedEvenIfDemotedAndDown() {
        val out = StreamQueue.order(listOf(c("only", "down", 0.0)), setOf("only"), emptyMap())
        assertEquals(listOf("only"), out.map { it.url })
    }
    @Test fun localScoreNeedsThreeSamples() {
        val s = StatSample(1000, 0, 3000, 3_600_000)
        assertNull(LocalScore.compute(listOf(s, s), "720p"))
        val v = LocalScore.compute(listOf(s, s, s), "720p")!!
        assertEquals(0.4 * 0.8 + 0.4 * 1.0 + 0.2 * 1.0, v, 1e-9)
    }
    @Test fun localScorePenalisesRebuffersAndLowBitrate() {
        val bad = StatSample(4000, 10, 500, 3_600_000)
        val v = LocalScore.compute(listOf(bad, bad, bad), "1080p")!!
        assertEquals(0.4 * 0.2 + 0.4 * 0.0 + 0.2 * 0.1, v, 1e-9)
    }
}
