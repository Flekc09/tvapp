package com.tvapp.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

const val SOURCE_IPTV = "iptv"

@Entity(tableName = "countries", primaryKeys = ["code", "importId"])
data class CountryEntity(val code: String, val name: String, val flag: String, val importId: Long)

@Entity(tableName = "categories", primaryKeys = ["id", "importId"])
data class CategoryEntity(val id: String, val name: String, val importId: Long)

@Entity(tableName = "channels", primaryKeys = ["id", "importId"],
    indices = [Index("importId"), Index("country"), Index("name")])
data class ChannelEntity(
    val id: String, val name: String, val altNames: String, val country: String?, val region: String?,
    val categories: String, val network: String?, val logo: String?, val adult: Boolean, val hasUp: Boolean,
    val source: String, val importId: Long,
    val bestHealth: String = "down", // "up" | "unverified" | "down": the best health among the channel's streams, set by the importer after the streams are in; the UI maps it to Working / Not checked / Not working (hasUp alone cannot tell the first two apart)
)

@Entity(tableName = "streams", indices = [Index("channelId"), Index("importId"), Index("url")])
data class StreamEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val channelId: String, val url: String, val format: String, val quality: String?,
    val referrer: String?, val userAgent: String?, val health: String, val uptime7d: Double,
    val responseMs: Int?, val score: Double, val source: String, val importId: Long,
)
