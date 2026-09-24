package com.tvapp.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "favorites")
data class FavoriteEntity(@PrimaryKey val channelId: String, val position: Int, val name: String = "")

@Entity(tableName = "recents")
data class RecentEntity(@PrimaryKey val channelId: String, val lastWatchedMs: Long)


@Entity(tableName = "stream_stats", indices = [Index("streamUrl")])
data class StreamStatEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val streamUrl: String, val ttffMs: Int?, val rebuffers: Int, val bitrateKbps: Int?, val playedMs: Long, val recordedElapsedMs: Long,
)

@Entity(tableName = "stream_failures", indices = [Index("streamUrl")])
data class StreamFailureEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val streamUrl: String, val failedElapsedMs: Long)

@Entity(tableName = "channel_status")
data class ChannelStatusEntity(@PrimaryKey val channelId: String, val status: String, val failedDays: String)

@Entity(tableName = "settings")
data class SettingEntity(@PrimaryKey val key: String, val value: String)
