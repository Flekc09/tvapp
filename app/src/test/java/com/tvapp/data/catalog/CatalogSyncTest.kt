package com.tvapp.data.catalog

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.GZIPOutputStream

class CatalogSyncTest {
    private class FakeImporter : ImportSink {
        var localVersion: Long? = null; var received: ByteArray? = null; val settings = HashMap<String, String>()
        override suspend fun activeVersion() = localVersion
        override suspend fun importStream(input: InputStream, onChannels: (Int) -> Unit): ImportResult { received = input.readBytes(); localVersion = 2; onChannels(1); return ImportResult(1, 2, 1) }
        override suspend fun setting(key: String) = settings[key]
        override suspend fun setSetting(key: String, value: String) { settings[key] = value }
    }
    private fun gz(s: String) = ByteArrayOutputStream().also { GZIPOutputStream(it).use { g -> g.write(s.toByteArray()) } }.toByteArray()

    @Test fun updateAvailableWhenRemoteNewer() = runTest {
        val server = MockWebServer(); server.enqueue(MockResponse().setBody("""{"version":2,"bytes":10}""")); server.start()
        val imp = FakeImporter().apply { localVersion = 1 }
        val sync = CatalogSync(OkHttpClient(), imp) { server.url("/").toString().trimEnd('/') }
        val c = sync.checkLatest()
        assertEquals(2L, c.remoteVersion); assertEquals(1L, c.localVersion); assertTrue(c.updateAvailable)
        server.shutdown()
    }
    @Test fun noUpdateWhenSameVersionOrOffline() = runTest {
        val server = MockWebServer(); server.enqueue(MockResponse().setBody("""{"version":2,"bytes":10}""")); server.start()
        val imp = FakeImporter().apply { localVersion = 2 }
        val sync = CatalogSync(OkHttpClient(), imp) { server.url("/").toString().trimEnd('/') }
        assertFalse(sync.checkLatest().updateAvailable)
        server.shutdown()
        val offline = sync.checkLatest()
        assertEquals(null, offline.remoteVersion); assertFalse(offline.updateAvailable)
    }
    @Test fun recordPendingUpdateStoresTheFlagAndDownloadClearsIt() = runTest {
        val body = gz("""{"version":2}""")
        val server = MockWebServer(); server.enqueue(MockResponse().setBody("""{"version":2,"bytes":10}""")); server.enqueue(MockResponse().setBody(Buffer().write(body))); server.start()
        val imp = FakeImporter().apply { localVersion = 1 }
        val sync = CatalogSync(OkHttpClient(), imp) { server.url("/").toString().trimEnd('/') }
        assertTrue(sync.recordPendingUpdate()); assertEquals("true", imp.settings["pending_update"])
        sync.download { _, _, _ -> }; assertEquals("false", imp.settings["pending_update"])
        server.shutdown()
    }
    @Test fun downloadStreamsBodyToImporterWithProgress() = runTest {
        val body = gz("""{"version":2}""")
        val server = MockWebServer(); server.enqueue(MockResponse().setBody(Buffer().write(body))); server.start()
        val imp = FakeImporter()
        val sync = CatalogSync(OkHttpClient(), imp) { server.url("/").toString().trimEnd('/') }
        var last = 0L
        sync.download { read, _, _ -> last = read }
        assertEquals(body.toList(), imp.received!!.toList()); assertEquals(body.size.toLong(), last)
        server.shutdown()
    }
}
