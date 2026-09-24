package com.tvapp.data.catalog

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tvapp.TvApp

class CatalogSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as TvApp).container
        // The worker is the idle gate (spec 5.2): never import while someone is watching or within 10 minutes of watching.
        if (c.playerController.state.value.playing != null) return Result.retry()
        val lastPlaying = c.db.local().setting(CatalogSync.KEY_LAST_PLAYING)?.toLongOrNull() ?: 0L
        if (System.currentTimeMillis() - lastPlaying < CatalogSync.IDLE_MS) return Result.retry()
        return try {
            if (c.catalogSync.recordPendingUpdate()) c.catalogSync.download { _, _, _ -> }
            Result.success()
        } catch (e: Exception) { Result.retry() }
    }
}
