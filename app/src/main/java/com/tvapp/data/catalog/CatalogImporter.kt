package com.tvapp.data.catalog

import androidx.room.withTransaction
import com.tvapp.data.db.AppDatabase
import com.tvapp.data.db.CategoryEntity
import com.tvapp.data.db.ChannelEntity
import com.tvapp.data.db.CountryEntity
import com.tvapp.data.db.SettingEntity
import com.tvapp.data.db.StreamEntity
import java.io.InputStream

data class ImportResult(val importId: Long, val version: Long, val channels: Int)

class CatalogImporter(private val db: AppDatabase, private val parser: CatalogParser) {
    companion object {
        const val KEY_ACTIVE = "active_import_id"
        const val KEY_VERSION = "catalog_version"
        const val KEY_GENERATED = "catalog_generated_at"
    }

    suspend fun activeImportId(): Long? = db.local().setting(KEY_ACTIVE)?.toLongOrNull()
    suspend fun activeImportIds(): List<Long> = listOfNotNull(activeImportId())

    private class Header(var version: Long = -1L, var generatedAt: String = "", var channels: Int = 0)

    suspend fun import(input: InputStream, onChannels: (Int) -> Unit = {}): ImportResult {
        val importId = System.currentTimeMillis()
        val h = Header()
        val sink = object : CatalogSink {
            override suspend fun header(version: Long, generatedAt: String) { h.version = version; h.generatedAt = generatedAt }
            override suspend fun countries(rows: List<CountryEntity>) = db.catalog().insertCountries(rows)
            override suspend fun categories(rows: List<CategoryEntity>) = db.catalog().insertCategories(rows)
            override suspend fun channels(rows: List<ChannelEntity>) { db.catalog().insertChannels(rows); h.channels += rows.size; onChannels(h.channels) }
            override suspend fun streams(rows: List<StreamEntity>) = db.catalog().insertStreams(rows)
        }
        try {
            parser.parse(input, importId, sink)
            db.catalog().setBestHealth(importId) // one UPDATE per import: up if any stream is up, else unverified if any is unverified, else down
            val version = h.version; val generatedAt = h.generatedAt; val channels = h.channels
            val previous = activeImportId()
            db.withTransaction {
                db.local().setSetting(SettingEntity(KEY_ACTIVE, importId.toString()))
                db.local().setSetting(SettingEntity(KEY_VERSION, version.toString()))
                db.local().setSetting(SettingEntity(KEY_GENERATED, generatedAt))
                if (previous != null && previous != importId) deleteImport(previous)
            }
            return ImportResult(importId, version, channels)
        } catch (e: Exception) {
            deleteImport(importId)
            throw e
        }
    }

    private suspend fun deleteImport(importId: Long) {
        db.catalog().deleteStreamsOfImport(importId)
        db.catalog().deleteChannelsOfImport(importId)
        db.catalog().deleteCountriesOfImport(importId)
        db.catalog().deleteCategoriesOfImport(importId)
    }

    suspend fun cleanupOrphans() {
        val active = activeImportId()
        for (id in db.catalog().iptvImportIds()) if (id != active) deleteImport(id)
    }
}
