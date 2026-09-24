package com.tvapp.ui.state

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PreviewControllerTest {
    @Test fun switchesAfterDwellOnlyForTheLastFocusedChannel() = runTest {
        val switched = mutableListOf<String>()
        val p = PreviewController(this, 700, currentChannelId = "cur") { switched += it }
        p.focus("a"); advanceTimeBy(300); p.focus("b"); advanceTimeBy(699)
        assertEquals(emptyList<String>(), switched)
        advanceTimeBy(2); assertEquals(listOf("b"), switched)
    }
    @Test fun focusingTheCurrentChannelNeverSwitches() = runTest {
        val switched = mutableListOf<String>()
        val p = PreviewController(this, 700, currentChannelId = "cur") { switched += it }
        p.focus("cur"); advanceTimeBy(1000); assertEquals(emptyList<String>(), switched)
    }
    @Test fun stopCancelsPending() = runTest {
        val switched = mutableListOf<String>()
        val p = PreviewController(this, 700, currentChannelId = null) { switched += it }
        p.focus("a"); advanceTimeBy(500); p.stop(); advanceTimeBy(500); assertEquals(emptyList<String>(), switched)
    }
}
