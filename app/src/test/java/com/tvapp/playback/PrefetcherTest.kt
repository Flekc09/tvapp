package com.tvapp.playback

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PrefetcherTest {
    private fun c(url: String, ua: String? = null, ref: String? = null) = Candidate(url, "up", 0.5, null, "hls", ref, ua)

    @Test fun focusFetchesOnlyTheLastCandidateAfterDebounce() = runTest {
        val server = MockWebServer(); repeat(3) { server.enqueue(MockResponse().setBody("#EXTM3U\n")) }; server.start()
        val p = Prefetcher(OkHttpClient(), this, debounceMs = 300, ioDispatcher = StandardTestDispatcher(testScheduler))
        p.focus(c(server.url("/a.m3u8").toString())); advanceTimeBy(100)
        p.focus(c(server.url("/b.m3u8").toString())); advanceTimeBy(301); advanceUntilIdle()
        assertEquals(1, server.requestCount); assertEquals("/b.m3u8", server.takeRequest().path)
        server.shutdown()
    }
    @Test fun neighborsFetchesBothWithTheirHeaders() = runTest {
        val server = MockWebServer(); repeat(2) { server.enqueue(MockResponse().setBody("#EXTM3U\n")) }; server.start()
        val p = Prefetcher(OkHttpClient(), this, debounceMs = 0, ioDispatcher = StandardTestDispatcher(testScheduler))
        p.neighbors(c(server.url("/p.m3u8").toString(), ua = "UA-P"), c(server.url("/n.m3u8").toString(), ref = "http://r/")); advanceUntilIdle()
        assertEquals(2, server.requestCount)
        val reqs = listOf(server.takeRequest(), server.takeRequest()).associateBy { it.path }
        assertEquals("UA-P", reqs["/p.m3u8"]!!.getHeader("User-Agent")); assertEquals("http://r/", reqs["/n.m3u8"]!!.getHeader("Referer"))
        server.shutdown()
    }
    @Test fun onlyPlaylistFormatsArePrefetched() = runTest {
        val server = MockWebServer(); server.enqueue(MockResponse().setBody("#EXTM3U\n")); server.start()
        val p = Prefetcher(OkHttpClient(), this, debounceMs = 0, ioDispatcher = StandardTestDispatcher(testScheduler))
        p.focus(Candidate(server.url("/live.ts").toString(), "up", 0.5, null, "ts", null, null)); advanceUntilIdle()
        p.focus(Candidate(server.url("/x").toString(), "up", 0.5, null, "unknown", null, null)); advanceUntilIdle()
        assertEquals(0, server.requestCount)
        server.shutdown()
    }
    @Test fun errorsAreSwallowed() = runTest {
        val p = Prefetcher(OkHttpClient(), this, debounceMs = 0, ioDispatcher = StandardTestDispatcher(testScheduler))
        p.focus(c("http://127.0.0.1:1/never")); advanceUntilIdle() // connection refused, no exception escapes
    }
}
