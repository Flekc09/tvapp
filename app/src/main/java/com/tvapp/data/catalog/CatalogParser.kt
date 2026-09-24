package com.tvapp.data.catalog

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.tvapp.data.db.CategoryEntity
import com.tvapp.data.db.ChannelEntity
import com.tvapp.data.db.CountryEntity
import com.tvapp.data.db.SOURCE_IPTV
import com.tvapp.data.db.StreamEntity
import java.io.InputStream
import java.io.InputStreamReader
import java.util.zip.GZIPInputStream

class CatalogFormatException(msg: String, cause: Throwable? = null) : Exception(msg, cause)

interface CatalogSink {
    suspend fun header(version: Long, generatedAt: String)
    suspend fun countries(rows: List<CountryEntity>)
    suspend fun categories(rows: List<CategoryEntity>)
    suspend fun channels(rows: List<ChannelEntity>)
    suspend fun streams(rows: List<StreamEntity>)
}

class CatalogParser(private val batchSize: Int = 1000) {

    suspend fun parse(input: InputStream, importId: Long, sink: CatalogSink) {
        try {
            JsonReader(InputStreamReader(GZIPInputStream(input), Charsets.UTF_8)).use { r ->
                var version = -1L; var generatedAt = ""
                r.beginObject()
                while (r.hasNext()) {
                    when (r.nextName()) {
                        "version" -> version = r.nextLong()
                        "generatedAt" -> generatedAt = r.nextString()
                        "countries" -> batch(r, { readCountry(r, importId) }, sink::countries)
                        "categories" -> batch(r, { readCategory(r, importId) }, sink::categories)
                        "channels" -> batch(r, { readChannel(r, importId) }, sink::channels)
                        "streams" -> batch(r, { readStream(r, importId) }, sink::streams)
                        else -> r.skipValue()
                    }
                }
                r.endObject()
                if (version < 0) throw CatalogFormatException("missing version")
                sink.header(version, generatedAt) // after the whole document, so key order does not matter
            }
        } catch (e: CatalogFormatException) { throw e }
        catch (e: Exception) { throw CatalogFormatException("catalog parse failed: ${e.message}", e) }
    }

    private suspend fun <T> batch(r: JsonReader, readOne: () -> T, emit: suspend (List<T>) -> Unit) {
        val buf = ArrayList<T>(batchSize)
        r.beginArray()
        while (r.hasNext()) { buf += readOne(); if (buf.size >= batchSize) { emit(ArrayList(buf)); buf.clear() } }
        r.endArray()
        if (buf.isNotEmpty()) emit(buf)
    }

    private fun JsonReader.nextStringOrNull(): String? = if (peek() == JsonToken.NULL) { nextNull(); null } else nextString()
    // responseMs is an integer in spec 4.4, but a fractional millisecond value must round rather than fail the whole import (Opus adversarial review 2026-09-23, blocker 1).
    private fun JsonReader.nextIntOrNull(): Int? = if (peek() == JsonToken.NULL) { nextNull(); null } else Math.round(nextDouble()).toInt()
    private fun JsonReader.stringList(): List<String> { val l = ArrayList<String>(); beginArray(); while (hasNext()) l += nextString(); endArray(); return l }

    private fun readCountry(r: JsonReader, importId: Long): CountryEntity {
        var code = ""; var name = ""; var flag = ""
        r.beginObject(); while (r.hasNext()) when (r.nextName()) { "code" -> code = r.nextString(); "name" -> name = r.nextString(); "flag" -> flag = r.nextString(); else -> r.skipValue() }; r.endObject()
        return CountryEntity(code, name, flag, importId)
    }
    private fun readCategory(r: JsonReader, importId: Long): CategoryEntity {
        var id = ""; var name = ""
        r.beginObject(); while (r.hasNext()) when (r.nextName()) { "id" -> id = r.nextString(); "name" -> name = r.nextString(); else -> r.skipValue() }; r.endObject()
        return CategoryEntity(id, name, importId)
    }
    private fun readChannel(r: JsonReader, importId: Long): ChannelEntity {
        var id = ""; var name = ""; var alt = emptyList<String>(); var country: String? = null; var region: String? = null
        var cats = emptyList<String>(); var network: String? = null; var logo: String? = null; var adult = false; var hasUp = false
        r.beginObject()
        while (r.hasNext()) when (r.nextName()) {
            "id" -> id = r.nextString(); "name" -> name = r.nextString(); "altNames" -> alt = r.stringList()
            "country" -> country = r.nextStringOrNull(); "region" -> region = r.nextStringOrNull()
            "categories" -> cats = r.stringList(); "network" -> network = r.nextStringOrNull(); "logo" -> logo = r.nextStringOrNull()
            "adult" -> adult = r.nextBoolean(); "hasUp" -> hasUp = r.nextBoolean(); else -> r.skipValue()
        }
        r.endObject()
        return ChannelEntity(id, name, alt.joinToString("|"), country, region, cats.joinToString("|"), network, logo, adult, hasUp, SOURCE_IPTV, importId)
    }
    private fun readStream(r: JsonReader, importId: Long): StreamEntity {
        var channel = ""; var url = ""; var format = "unknown"; var quality: String? = null; var referrer: String? = null; var ua: String? = null
        var health = "down"; var uptime = 0.0; var responseMs: Int? = null; var score = 0.0
        r.beginObject()
        while (r.hasNext()) when (r.nextName()) {
            "channel" -> channel = r.nextString(); "url" -> url = r.nextString(); "format" -> format = r.nextString()
            "quality" -> quality = r.nextStringOrNull(); "referrer" -> referrer = r.nextStringOrNull(); "userAgent" -> ua = r.nextStringOrNull()
            "health" -> health = r.nextString(); "uptime7d" -> uptime = r.nextDouble(); "responseMs" -> responseMs = r.nextIntOrNull()
            "score" -> score = r.nextDouble(); else -> r.skipValue()
        }
        r.endObject()
        return StreamEntity(channelId = channel, url = url, format = format, quality = quality, referrer = referrer, userAgent = ua,
            health = health, uptime7d = uptime, responseMs = responseMs, score = score, source = SOURCE_IPTV, importId = importId)
    }
}
