package com.tvapp.data.catalog

import com.tvapp.data.db.CategoryEntity
import com.tvapp.data.db.ChannelEntity
import com.tvapp.data.db.CountryEntity
import com.tvapp.data.db.StreamEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

class CatalogParserTest {
    private fun gz(s: String): ByteArray = ByteArrayOutputStream().also { GZIPOutputStream(it).use { g -> g.write(s.toByteArray()) } }.toByteArray()

    private class Sink : CatalogSink {
        var version = 0L; var generatedAt = ""
        val countries = mutableListOf<CountryEntity>(); val categories = mutableListOf<CategoryEntity>()
        val channels = mutableListOf<ChannelEntity>(); val streams = mutableListOf<StreamEntity>()
        var channelBatches = 0
        override suspend fun header(version: Long, generatedAt: String) { this.version = version; this.generatedAt = generatedAt }
        override suspend fun countries(rows: List<CountryEntity>) { countries += rows }
        override suspend fun categories(rows: List<CategoryEntity>) { categories += rows }
        override suspend fun channels(rows: List<ChannelEntity>) { channels += rows; channelBatches++ }
        override suspend fun streams(rows: List<StreamEntity>) { streams += rows }
    }

    private val doc = """{
      "version": 1758520800, "generatedAt": "2026-09-22T06:00:00Z",
      "countries": [{"code":"US","name":"United States","flag":"🇺🇸"}],
      "categories": [{"id":"news","name":"News"},{"id":"other","name":"Other"}],
      "channels": [
        {"id":"ABC.us@WSOCTV","name":"ABC · WSOC-TV","altNames":["ABC East"],"country":"US","region":"Charlotte",
         "categories":["general","news"],"network":"ABC","logo":"http://l/a.png","adult":false,"hasUp":true,"extra":{"ignored":1}},
        {"id":"synthetic:abc","name":"X","altNames":[],"country":null,"region":null,"categories":["other"],"network":null,"logo":null,"adult":true,"hasUp":false}
      ],
      "streams": [
        {"channel":"ABC.us@WSOCTV","url":"http://a/1.m3u8","format":"hls","quality":"720p","referrer":"http://r","userAgent":"UA",
         "health":"up","uptime7d":0.86,"responseMs":412,"score":0.81,"checkedAt":"2026-09-22T06:00:00Z"},
        {"channel":"synthetic:abc","url":"http://b/1.ts","format":"ts","quality":null,"referrer":null,"userAgent":null,
         "health":"down","uptime7d":0,"responseMs":null,"score":0.1,"checkedAt":"x"}
      ]
    }"""

    @Test fun parsesEverythingIntoEntities() = runTest {
        val sink = Sink()
        CatalogParser(batchSize = 1).parse(ByteArrayInputStream(gz(doc)), importId = 7, sink)
        assertEquals(1758520800L, sink.version); assertEquals("2026-09-22T06:00:00Z", sink.generatedAt)
        assertEquals(listOf(CountryEntity("US", "United States", "🇺🇸", 7)), sink.countries)
        assertEquals(2, sink.categories.size)
        assertEquals(2, sink.channelBatches)
        val abc = sink.channels[0]
        assertEquals("ABC.us@WSOCTV", abc.id); assertEquals("ABC East", abc.altNames); assertEquals("Charlotte", abc.region)
        assertEquals("general|news", abc.categories); assertEquals(false, abc.adult); assertEquals(true, abc.hasUp); assertEquals(7L, abc.importId)
        assertEquals(true, sink.channels[1].adult); assertEquals(null, sink.channels[1].country)
        val s0 = sink.streams[0]
        assertEquals("http://r", s0.referrer); assertEquals("UA", s0.userAgent); assertEquals(412, s0.responseMs); assertEquals(0.81, s0.score, 1e-9)
        assertEquals(null, sink.streams[1].responseMs); assertEquals("iptv", s0.source)
    }

    @Test fun headerArrivesAfterBodyRegardlessOfKeyOrder() = runTest {
        val sink = Sink()
        val reordered = doc.replace("\"version\": 1758520800, \"generatedAt\": \"2026-09-22T06:00:00Z\",", "").replace("\"streams\": [", "\"generatedAt\": \"g\", \"version\": 5, \"streams\": [")
        CatalogParser().parse(ByteArrayInputStream(gz(reordered)), 1, sink)
        assertEquals(5L, sink.version); assertEquals("g", sink.generatedAt)
    }

    @Test fun truncatedInputThrows() = runTest {
        val bytes = gz(doc.substring(0, doc.length / 2))
        assertThrows(CatalogFormatException::class.java) {
            kotlinx.coroutines.runBlocking { CatalogParser().parse(ByteArrayInputStream(bytes), 1, Sink()) }
        }
    }

    // Contract with the catalog job: spec 4.4 says responseMs is an integer, and the job rounds it, but an unrounded
    // performance.now() value ("responseMs":694.155667 came out of an unpatched run) must never fail the import.
    @Test fun fractionalResponseMsRoundsInsteadOfFailingTheImport() = runTest {
        val sink = Sink()
        CatalogParser().parse(ByteArrayInputStream(gz(doc.replace("\"responseMs\":412", "\"responseMs\":694.155667"))), 1, sink)
        assertEquals(694, sink.streams[0].responseMs)
    }
}
