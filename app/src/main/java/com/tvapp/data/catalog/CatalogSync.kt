package com.tvapp.data.catalog

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import java.util.concurrent.TimeUnit

interface ImportSink {
    suspend fun activeVersion(): Long?
    suspend fun importStream(input: InputStream, onChannels: (Int) -> Unit): ImportResult
    suspend fun setting(key: String): String?
    suspend fun setSetting(key: String, value: String)
}

data class LatestCheck(val remoteVersion: Long?, val localVersion: Long?, val updateAvailable: Boolean)

class CatalogSync(private val http: OkHttpClient, private val sink: ImportSink, private val baseUrl: suspend () -> String) : com.tvapp.ui.state.Downloader {
    companion object {
        const val KEY_PENDING = "pending_update"; const val KEY_LAST_PLAYING = "last_playing_wall"; const val IDLE_MS = 10 * 60_000L
        const val PERIODIC = "catalog-sync-periodic"; const val IDLE = "catalog-sync-idle"
        // No device-idle constraint: on a TV it would only fire after long standby and still would not stop a download mid-game.
        // The worker checks idleness itself (see CatalogSyncWorker).
        fun schedulePeriodic(context: Context) {
            val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<CatalogSyncWorker>(24, TimeUnit.HOURS).setConstraints(constraints).build())
        }
        fun runWhenIdle(context: Context) {
            val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            WorkManager.getInstance(context).enqueueUniqueWork(IDLE, ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<CatalogSyncWorker>().setConstraints(constraints).build())
        }
    }

    suspend fun hasCatalog(): Boolean = sink.activeVersion() != null
    suspend fun recordPendingUpdate(): Boolean { val c = checkLatest(); sink.setSetting(KEY_PENDING, c.updateAvailable.toString()); return c.updateAvailable }

    suspend fun checkLatest(): LatestCheck = withContext(Dispatchers.IO) {
        val local = sink.activeVersion()
        val remote = try {
            http.newCall(Request.Builder().url("${baseUrl()}/latest.json").header("Cache-Control", "no-cache").build()).execute().use { r ->
                if (!r.isSuccessful) null else JsonParser.parseString(r.body!!.string()).asJsonObject["version"].asLong
            }
        } catch (e: Exception) { null }
        LatestCheck(remote, local, remote != null && remote != local)
    }

    override suspend fun download(onProgress: (Long, Long, Int) -> Unit): ImportResult = withContext(Dispatchers.IO) {
        var channels = 0; var read = 0L; var total = 0L
        fun report() = onProgress(read, total, channels)
        http.newCall(Request.Builder().url("${baseUrl()}/catalog.json.gz").build()).execute().use { r ->
            if (!r.isSuccessful) throw CatalogFormatException("catalog download HTTP ${r.code}")
            total = r.body!!.contentLength()
            val counting = object : InputStream() {
                val inner = r.body!!.byteStream()
                override fun read(): Int = inner.read().also { if (it >= 0) { read++; report() } }
                override fun read(b: ByteArray, off: Int, len: Int): Int = inner.read(b, off, len).also { if (it > 0) { read += it; report() } }
                override fun close() = inner.close()
            }
            sink.importStream(counting) { n -> channels = n; report() }.also { sink.setSetting(KEY_PENDING, "false") }
        }
    }
}

class ImporterSink(private val importer: CatalogImporter, private val db: com.tvapp.data.db.AppDatabase) : ImportSink {
    override suspend fun activeVersion(): Long? = db.local().setting(CatalogImporter.KEY_VERSION)?.toLongOrNull()
    override suspend fun importStream(input: InputStream, onChannels: (Int) -> Unit): ImportResult = importer.import(input, onChannels)
    override suspend fun setting(key: String): String? = db.local().setting(key)
    override suspend fun setSetting(key: String, value: String) = db.local().setSetting(com.tvapp.data.db.SettingEntity(key, value))
}
