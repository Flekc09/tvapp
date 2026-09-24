package com.tvapp.playback

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TuneDebouncerTest {
    @Test fun onlyLastRequestInWindowTunes() = runTest {
        val tuned = mutableListOf<String>()
        val d = TuneDebouncer(this, 300) { tuned += it }
        d.request("a"); advanceTimeBy(100); d.request("b"); advanceTimeBy(100); d.request("c")
        advanceTimeBy(301)
        assertEquals(listOf("c"), tuned)
        d.request("d"); advanceTimeBy(301)
        assertEquals(listOf("c", "d"), tuned)
    }
}
