package com.tvapp.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

// An abstract class, not an interface: Room documents @Transaction bodies only for abstract DAO classes.
@Dao
abstract class LocalDao {
    @Query("SELECT * FROM favorites ORDER BY position") abstract fun favorites(): Flow<List<FavoriteEntity>>
    @Query("SELECT * FROM favorites ORDER BY position") abstract suspend fun favoritesNow(): List<FavoriteEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun upsertFavorite(f: FavoriteEntity)
    @Query("DELETE FROM favorites WHERE channelId = :channelId") abstract suspend fun deleteFavorite(channelId: String)
    @Transaction open suspend fun setFavoriteOrder(ids: List<String>) {
        val names = favoritesNow().associate { it.channelId to it.name }
        ids.forEachIndexed { i, id -> upsertFavorite(FavoriteEntity(id, i, names[id] ?: "")) }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun upsertRecent(r: RecentEntity)
    @Query("SELECT * FROM recents ORDER BY lastWatchedMs DESC LIMIT 50") abstract fun recents(): Flow<List<RecentEntity>>
    @Query("SELECT * FROM recents ORDER BY lastWatchedMs DESC LIMIT 2") abstract suspend fun lastTwo(): List<RecentEntity>


    @Insert abstract suspend fun insertStat(s: StreamStatEntity)
    @Query("SELECT * FROM stream_stats WHERE streamUrl = :url ORDER BY recordedElapsedMs DESC LIMIT 20") abstract suspend fun statsForStream(url: String): List<StreamStatEntity>

    @Insert abstract suspend fun insertFailure(f: StreamFailureEntity)
    @Query("SELECT streamUrl FROM stream_failures WHERE failedElapsedMs >= :sinceElapsed") abstract suspend fun failedUrlsSince(sinceElapsed: Long): List<String>
    @Query("DELETE FROM stream_failures WHERE streamUrl = :url") abstract suspend fun clearFailures(url: String)
    @Query("DELETE FROM stream_failures WHERE failedElapsedMs < :before") abstract suspend fun pruneFailures(before: Long)

    @Query("SELECT * FROM channel_status WHERE channelId = :channelId") abstract suspend fun channelStatus(channelId: String): ChannelStatusEntity?
    @Query("SELECT channelId FROM channel_status WHERE status = 'broken_here'") abstract suspend fun brokenHereIds(): List<String>
    @Query("SELECT channelId FROM channel_status WHERE status = 'broken_here'") abstract fun brokenHereIdsFlow(): Flow<List<String>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun upsertChannelStatus(s: ChannelStatusEntity)
    @Query("DELETE FROM channel_status WHERE channelId = :channelId") abstract suspend fun deleteChannelStatus(channelId: String)

    @Query("SELECT value FROM settings WHERE `key` = :key") abstract suspend fun setting(key: String): String?
    @Query("SELECT value FROM settings WHERE `key` = :key") abstract fun settingFlow(key: String): Flow<String?>
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun setSetting(s: SettingEntity)
    @Query("DELETE FROM settings WHERE `key` = :key") abstract suspend fun deleteSetting(key: String)
}
