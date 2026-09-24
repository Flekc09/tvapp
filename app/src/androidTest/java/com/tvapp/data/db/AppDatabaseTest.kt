package com.tvapp.data.db

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseTest {
    private lateinit var db: AppDatabase
    @Before fun setUp() { db = AppDatabase.inMemory(ApplicationProvider.getApplicationContext()) }
    @After fun tearDown() { db.close() }

    private fun ch(id: String, country: String?, cats: String, adult: Boolean = false, hasUp: Boolean = true, importId: Long = 1) =
        ChannelEntity(id, id, "", country, null, cats, null, null, adult, hasUp, SOURCE_IPTV, importId)

    @Test fun channelListFiltersCountryCategoryAdultAndHidden() = runTest {
        db.catalog().insertChannels(listOf(
            ch("a", "US", "news"), ch("b", "US", "sports|news"), ch("c", "FR", "news"),
            ch("x", "US", "xxx", adult = true), ch("d", "US", "news", hasUp = false), ch("h", "US", "news"),
        ))
        val ids = db.catalog().channelList(listOf(1), "US", "news", showAdult = false, showNoUp = false, hiddenIds = listOf("h")).first().map { it.id }
        assertEquals(listOf("a", "b"), ids)
        val all = db.catalog().channelList(listOf(1), null, null, showAdult = true, showNoUp = true, hiddenIds = emptyList()).first().map { it.id }
        assertEquals(listOf("a", "b", "c", "h", "x", "d"), all) // hasUp first, then name
    }

    @Test fun categoryMatchIsWholeToken() = runTest {
        db.catalog().insertChannels(listOf(ch("a", "US", "sportsnews"), ch("b", "US", "sports")))
        val ids = db.catalog().channelList(listOf(1), null, "sports", true, true, emptyList()).first().map { it.id }
        assertEquals(listOf("b"), ids)
    }

    @Test fun streamsOrderedByScore() = runTest {
        db.catalog().insertStreams(listOf(
            StreamEntity(channelId = "a", url = "u1", format = "hls", quality = null, referrer = null, userAgent = null, health = "up", uptime7d = 1.0, responseMs = 1, score = 0.5, source = SOURCE_IPTV, importId = 1),
            StreamEntity(channelId = "a", url = "u2", format = "hls", quality = null, referrer = null, userAgent = null, health = "up", uptime7d = 1.0, responseMs = 1, score = 0.9, source = SOURCE_IPTV, importId = 1),
        ))
        assertEquals(listOf("u2", "u1"), db.catalog().streamsForChannel("a", listOf(1)).map { it.url })
    }

    @Test fun favoritesReorderKeepsNames() = runTest {
        db.local().upsertFavorite(FavoriteEntity("a", 0, "Alpha")); db.local().upsertFavorite(FavoriteEntity("b", 1, "Beta")); db.local().upsertFavorite(FavoriteEntity("c", 2, "Gamma"))
        db.local().setFavoriteOrder(listOf("c", "a", "b"))
        assertEquals(listOf("c", "a", "b"), db.local().favoritesNow().map { it.channelId })
        assertEquals("Alpha", db.local().favoritesNow()[1].name)
    }

    @Test fun countryCountsUseTheSameFiltersAsTheList() = runTest {
        db.catalog().insertChannels(listOf(ch("a", "US", "news"), ch("x", "US", "xxx", adult = true), ch("d", "US", "news", hasUp = false), ch("h", "US", "news"), ch("o", "US", "other")))
        val strict = db.catalog().countryCounts(listOf(1), showAdult = false, showNoUp = false, hiddenIds = listOf("h"))
        assertEquals(1, strict.first { it.country == "US" }.n) // a; x adult, d no-up, h hidden, o "other"-only (Opus adversarial review 2026-09-23, major 13)
        val loose = db.catalog().countryCounts(listOf(1), showAdult = true, showNoUp = true, hiddenIds = emptyList())
        assertEquals(4, loose.first { it.country == "US" }.n) // o still out: the count describes the country's list
        assertEquals(listOf("a"), db.catalog().channelList(listOf(1), "US", null, false, false, listOf("h")).first().map { it.id }) // "other"-only channels stay out of a country list (spec 6)
        assertEquals(listOf("o"), db.catalog().channelList(listOf(1), "US", "other", false, false, emptyList()).first().map { it.id })
    }
}
