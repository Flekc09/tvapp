package com.tvapp.playback

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class Prefetcher(
    private val http: OkHttpClient, private val scope: CoroutineScope,
    private val debounceMs: Long = 300, private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private var focusJob: Job? = null
    private var neighborJob: Job? = null

    fun focus(candidate: Candidate?) {
        focusJob?.cancel()
        if (candidate == null) return
        focusJob = scope.launch { delay(debounceMs); fetch(candidate) }
    }

    fun neighbors(prev: Candidate?, next: Candidate?) {
        neighborJob?.cancel()
        neighborJob = scope.launch { delay(debounceMs); next?.let { fetch(it) }; prev?.let { fetch(it) } }
    }

    companion object { const val PLAYLIST_CAP_BYTES = 64L * 1024 }

    // Only playlists are warmed, and at most 64 KiB is read (Opus adversarial review 2026-09-23, major 18).
    private suspend fun fetch(c: Candidate) {
        if (c.format != "hls" && c.format != "dash") return
        withContext(ioDispatcher) {
            runCatching {
                val b = Request.Builder().url(c.url)
                c.referrer?.let { b.header("Referer", it) }
                c.userAgent?.let { b.header("User-Agent", it) }
                http.newCall(b.build()).execute().use { it.body?.source()?.request(PLAYLIST_CAP_BYTES) }
            }
        }
    }
}
