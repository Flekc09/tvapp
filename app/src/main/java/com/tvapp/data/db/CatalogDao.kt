package com.tvapp.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CatalogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertCountries(rows: List<CountryEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertCategories(rows: List<CategoryEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertChannels(rows: List<ChannelEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertStreams(rows: List<StreamEntity>)

    @Query("DELETE FROM channels WHERE importId = :importId") suspend fun deleteChannelsOfImport(importId: Long)
    @Query("DELETE FROM streams WHERE importId = :importId") suspend fun deleteStreamsOfImport(importId: Long)
    @Query("DELETE FROM countries WHERE importId = :importId") suspend fun deleteCountriesOfImport(importId: Long)
    @Query("DELETE FROM categories WHERE importId = :importId") suspend fun deleteCategoriesOfImport(importId: Long)
    @Query("SELECT DISTINCT importId FROM channels WHERE source = 'iptv'") suspend fun iptvImportIds(): List<Long>

    @Query("SELECT COUNT(*) FROM channels WHERE importId = :importId") suspend fun channelCount(importId: Long): Int

    @Query("SELECT * FROM channels WHERE id = :id AND importId IN (:importIds) LIMIT 1")
    suspend fun channel(id: String, importIds: List<Long>): ChannelEntity?

    @Query("SELECT * FROM streams WHERE channelId = :channelId AND importId IN (:importIds) ORDER BY score DESC")
    suspend fun streamsForChannel(channelId: String, importIds: List<Long>): List<StreamEntity>

    @Query("""SELECT * FROM channels WHERE importId IN (:importIds)
        AND (:country IS NULL OR country = :country)
        AND (:category IS NULL OR ('|' || categories || '|') LIKE '%|' || :category || '|%')
        AND (:category IS NOT NULL OR categories != 'other')
        AND (:showAdult OR adult = 0)
        AND (:showNoUp OR hasUp = 1)
        AND id NOT IN (:hiddenIds)
        ORDER BY hasUp DESC, name COLLATE NOCASE""")
    // Channels whose only category is "other" appear under Browse → Other and in Search, never in a country list or All (spec 6 default filters).
    fun channelList(importIds: List<Long>, country: String?, category: String?, showAdult: Boolean, showNoUp: Boolean, hiddenIds: List<String>): Flow<List<ChannelEntity>>

    @Query("""SELECT * FROM channels WHERE importId IN (:importIds) AND id IN (:ids)""")
    suspend fun channelsByIds(importIds: List<Long>, ids: List<String>): List<ChannelEntity>

    // Search applies the same default filters as every list, in SQL, so the count and the rows agree (Opus adversarial review 2026-09-23, major 9).
    @Query("""SELECT * FROM channels WHERE importId IN (:importIds) AND (:showAdult OR adult = 0)
        AND (:showNoUp OR hasUp = 1) AND id NOT IN (:hiddenIds)
        AND (name LIKE '%' || :term || '%' OR altNames LIKE '%' || :term || '%' OR network LIKE '%' || :term || '%'
             OR region LIKE '%' || :term || '%' OR categories LIKE '%' || :term || '%')
        ORDER BY hasUp DESC, name COLLATE NOCASE LIMIT 200""")
    suspend fun search(importIds: List<Long>, term: String, showAdult: Boolean, showNoUp: Boolean, hiddenIds: List<String>): List<ChannelEntity>
    // Task 14 derives the "why it matched" line in Kotlin from the returned row: the first of name, altNames, network, region, categories that contains the term, so no SQL projection is needed.

    @Query("SELECT * FROM countries WHERE importId IN (:importIds) ORDER BY name") suspend fun countries(importIds: List<Long>): List<CountryEntity>
    @Query("SELECT * FROM categories WHERE importId IN (:importIds) ORDER BY name") suspend fun categories(importIds: List<Long>): List<CategoryEntity>
    @Query("""UPDATE channels SET bestHealth = CASE
        WHEN EXISTS (SELECT 1 FROM streams s WHERE s.channelId = channels.id AND s.importId = :importId AND s.health = 'up') THEN 'up'
        WHEN EXISTS (SELECT 1 FROM streams s WHERE s.channelId = channels.id AND s.importId = :importId AND s.health = 'unverified') THEN 'unverified'
        ELSE 'down' END WHERE importId = :importId""")
    suspend fun setBestHealth(importId: Long)

    // Counts use exactly the filters channelList uses, so a count never disagrees with the list it describes (adversarial review 2026-09-23, major 15).
    // A country's count describes its "All categories" list, which never shows "other"-only channels (Opus adversarial review 2026-09-23, major 13).
    @Query("""SELECT country, COUNT(*) AS n FROM channels WHERE importId IN (:importIds) AND categories != 'other' AND (:showAdult OR adult = 0) AND (:showNoUp OR hasUp = 1) AND id NOT IN (:hiddenIds) GROUP BY country""")
    suspend fun countryCounts(importIds: List<Long>, showAdult: Boolean, showNoUp: Boolean, hiddenIds: List<String>): List<CountryCount>
    @Query("""SELECT categories FROM channels WHERE importId IN (:importIds) AND (:country IS NULL OR country = :country) AND (:showAdult OR adult = 0) AND (:showNoUp OR hasUp = 1) AND id NOT IN (:hiddenIds)""")
    suspend fun categoryStrings(importIds: List<Long>, country: String?, showAdult: Boolean, showNoUp: Boolean, hiddenIds: List<String>): List<String> // counted in Kotlin by splitting on '|'; country = null gives the worldwide counts behind the "Country: All" chip
    @Query("""SELECT COUNT(*) FROM channels WHERE importId IN (:importIds) AND (:showAdult OR adult = 0) AND (:showNoUp OR hasUp = 1) AND id NOT IN (:hiddenIds)
        AND (name LIKE '%' || :term || '%' OR altNames LIKE '%' || :term || '%' OR network LIKE '%' || :term || '%' OR region LIKE '%' || :term || '%' OR categories LIKE '%' || :term || '%')""")
    suspend fun searchCount(importIds: List<Long>, term: String, showAdult: Boolean, showNoUp: Boolean, hiddenIds: List<String>): Int // the "1,204 results" line; search itself stays LIMIT 200
}

data class CountryCount(val country: String?, val n: Int)
