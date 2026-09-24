package com.tvapp.data.catalog

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tvapp.data.db.AppDatabase
import com.tvapp.data.db.ChannelEntity
import com.tvapp.data.db.SOURCE_IPTV
import com.tvapp.data.db.StreamEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

@RunWith(AndroidJUnit4::class)
class CatalogImporterTest {
    private lateinit var db: AppDatabase
    private lateinit var importer: CatalogImporter
    @Before fun setUp() { db = AppDatabase.inMemory(ApplicationProvider.getApplicationContext()); importer = CatalogImporter(db, CatalogParser(batchSize = 2)) }
    @After fun tearDown() { db.close() }

    private fun gz(s: String) = ByteArrayInputStream(ByteArrayOutputStream().also { GZIPOutputStream(it).use { g -> g.write(s.toByteArray()) } }.toByteArray())
    private fun doc(version: Long, ids: List<String>) = """{"version":$version,"generatedAt":"g$version","countries":[],"categories":[],
        "channels":[${ids.joinToString(",") { """{"id":"$it","name":"$it","altNames":[],"country":"US","region":null,"categories":["news"],"network":null,"logo":null,"adult":false,"hasUp":true}""" }}],
        "streams":[${ids.joinToString(",") { """{"channel":"$it","url":"http://$it","format":"hls","quality":null,"referrer":null,"userAgent":null,"health":"up","uptime7d":1,"responseMs":1,"score":0.5,"checkedAt":"x"}""" }}]}"""

    @Test fun secondImportReplacesFirstAtomically() = runTest {
        val r1 = importer.import(gz(doc(1, listOf("a", "b", "c"))))
        assertEquals(3, r1.channels); assertEquals(r1.importId, importer.activeImportId())
        val r2 = importer.import(gz(doc(2, listOf("b", "c", "d", "e"))))
        assertEquals(r2.importId, importer.activeImportId())
        assertEquals(listOf(r2.importId), db.catalog().iptvImportIds())
        assertEquals(4, db.catalog().channelCount(r2.importId))
        assertEquals("2", db.local().setting("catalog_version"))
    }

    @Test fun failedImportLeavesOldCatalogActiveAndNoPartialRows() = runTest {
        val r1 = importer.import(gz(doc(1, listOf("a"))))
        val truncated = doc(2, listOf("x", "y", "z")).let { it.substring(0, it.length - 40) }
        assertThrows(CatalogFormatException::class.java) { kotlinx.coroutines.runBlocking { importer.import(gz(truncated)) } }
        assertEquals(r1.importId, importer.activeImportId())
        assertEquals(listOf(r1.importId), db.catalog().iptvImportIds())
    }

    @Test fun cleanupOrphansRemovesRowsOfInterruptedImport() = runTest {
        val r1 = importer.import(gz(doc(1, listOf("a"))))
        // Simulate an import that died mid-way (standby): rows exist under a new id but the flip never happened.
        db.catalog().insertChannels(listOf(ChannelEntity("zz", "zz", "", "US", null, "news", null, null, false, true, SOURCE_IPTV, 999L)))
        importer.cleanupOrphans()
        assertEquals(listOf(r1.importId), db.catalog().iptvImportIds())
        assertEquals(r1.importId, importer.activeImportId())
    }

    @Test fun bestHealthIsTheBestOfTheChannelsStreams() = runTest {
        // setBestHealth runs at the end of every import (Task 4 step 3); this pins the query itself.
        db.catalog().insertChannels(listOf(
            ChannelEntity("u", "Up", "", "US", null, "news", null, null, false, true, SOURCE_IPTV, 5),
            ChannelEntity("v", "Unverified", "", "US", null, "news", null, null, false, true, SOURCE_IPTV, 5),
            ChannelEntity("d", "Down", "", "US", null, "news", null, null, false, false, SOURCE_IPTV, 5)))
        db.catalog().insertStreams(listOf(
            StreamEntity(channelId = "u", url = "http://u/1", format = "hls", quality = null, referrer = null, userAgent = null, health = "down", uptime7d = 0.0, responseMs = null, score = 0.1, source = SOURCE_IPTV, importId = 5),
            StreamEntity(channelId = "u", url = "http://u/2", format = "hls", quality = null, referrer = null, userAgent = null, health = "up", uptime7d = 0.9, responseMs = null, score = 0.9, source = SOURCE_IPTV, importId = 5),
            StreamEntity(channelId = "v", url = "http://v/1", format = "hls", quality = null, referrer = null, userAgent = null, health = "unverified", uptime7d = 0.5, responseMs = null, score = 0.5, source = SOURCE_IPTV, importId = 5),
            StreamEntity(channelId = "d", url = "http://d/1", format = "hls", quality = null, referrer = null, userAgent = null, health = "down", uptime7d = 0.0, responseMs = null, score = 0.1, source = SOURCE_IPTV, importId = 5)))
        db.catalog().setBestHealth(5)
        assertEquals(listOf("up", "unverified", "down"), db.catalog().channelsByIds(listOf(5), listOf("u", "v", "d")).sortedBy { it.id }.let { l -> listOf("u", "v", "d").map { id -> l.first { it.id == id }.bestHealth } })
    }
}
