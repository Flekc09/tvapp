package com.tvapp.playback

import androidx.media3.common.MimeTypes
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tvapp.net.Http
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayerFactoryTest {
    @Test fun mediaItemCarriesMimeAndHeadersReachServer() {
        val server = MockWebServer(); server.enqueue(MockResponse().setBody("#EXTM3U\n")); server.start()
        val c = Candidate(server.url("/x.m3u8").toString(), "up", 0.5, "720p", "hls", "http://ref/", "CustomUA")
        val item = PlayerFactory.mediaItem(c)
        assertEquals(MimeTypes.APPLICATION_M3U8, item.localConfiguration!!.mimeType)
        val ds = PlayerFactory.dataSourceFactory(Http.client(), c).createDataSource()
        ds.open(androidx.media3.datasource.DataSpec(android.net.Uri.parse(c.url))); ds.close()
        val req = server.takeRequest()
        assertEquals("http://ref/", req.getHeader("Referer")); assertEquals("CustomUA", req.getHeader("User-Agent"))
        server.enqueue(MockResponse().setBody("#EXTM3U\n"))
        val plain = c.copy(referrer = null, userAgent = null)
        PlayerFactory.dataSourceFactory(Http.client(), plain).createDataSource().also { it.open(androidx.media3.datasource.DataSpec(android.net.Uri.parse(c.url))); it.close() }
        assertEquals(com.tvapp.BuildConfig.USER_AGENT, server.takeRequest().getHeader("User-Agent"))
        server.shutdown()
    }
    @Test fun playerBuilds() {
        // ExoPlayer binds to the creating thread's Looper, or the main Looper when it has none, as the test thread does not:
        // build and release on main so both calls happen on the player's own thread.
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val p = PlayerFactory.create(ApplicationProvider.getApplicationContext(), Http.client()); p.release()
        }
    }
    @Test fun tunePolicyFailsFastOnlyBeforeTheFirstFrame() {
        var beforeFirstFrame = true
        val p = TunePolicy { beforeFirstFrame }
        assertEquals(0, p.getMinimumLoadableRetryCount(androidx.media3.common.C.DATA_TYPE_MANIFEST))
        beforeFirstFrame = false
        assertEquals(androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy.DEFAULT_MIN_LOADABLE_RETRY_COUNT, p.getMinimumLoadableRetryCount(androidx.media3.common.C.DATA_TYPE_MANIFEST))
    }
}
