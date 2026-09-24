// Media3 marks these classes @UnstableApi, an androidx.annotation.RequiresOptIn marker that Android Lint enforces
// (UnsafeOptInUsageError), not the Kotlin compiler. Every file that uses them opts in like this.
@file:OptIn(UnstableApi::class)

package com.tvapp.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import okhttp3.OkHttpClient

/**
 * Until the first frame a load error fails the stream at once (spec 5.5, "a player error fails the stream immediately"): Media3's
 * default retries a 404 three times over about 3 s, most of a stream's 4 s cutoff. After the first frame the defaults ride out a blip.
 * (Opus adversarial review 2026-09-23, major 11.)
 */
class TunePolicy(private val beforeFirstFrame: () -> Boolean) : DefaultLoadErrorHandlingPolicy() {
    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long =
        if (beforeFirstFrame()) C.TIME_UNSET else super.getRetryDelayMsFor(loadErrorInfo)
    override fun getMinimumLoadableRetryCount(dataType: Int): Int =
        if (beforeFirstFrame()) 0 else super.getMinimumLoadableRetryCount(dataType)
}

object PlayerFactory {
    const val BUFFER_FOR_PLAYBACK_MS = 1000
    const val MIN_BUFFER_MS = 15_000
    const val MAX_BUFFER_MS = 30_000

    fun loadControl() = DefaultLoadControl.Builder()
        .setBufferDurationsMs(MIN_BUFFER_MS, MAX_BUFFER_MS, BUFFER_FOR_PLAYBACK_MS, BUFFER_FOR_PLAYBACK_MS)
        .setBackBuffer(0, false).build()

    fun dataSourceFactory(http: OkHttpClient, c: Candidate): DataSource.Factory {
        val okhttp = OkHttpDataSource.Factory(http).apply {
            val props = HashMap<String, String>()
            c.referrer?.let { props["Referer"] = it }
            c.userAgent?.let { props["User-Agent"] = it }
            setDefaultRequestProperties(props)
        }
        return DataSource.Factory { okhttp.createDataSource() }
    }

    fun mediaItem(c: Candidate): MediaItem = MediaItem.Builder().setUri(c.url).apply {
        when (c.format) { "hls" -> setMimeType(MimeTypes.APPLICATION_M3U8); "dash" -> setMimeType(MimeTypes.APPLICATION_MPD) }
    }.build()

    fun create(context: Context, http: OkHttpClient): ExoPlayer =
        ExoPlayer.Builder(context).setLoadControl(loadControl()).build()

    fun mediaSourceFactory(http: OkHttpClient, c: Candidate, policy: LoadErrorHandlingPolicy) =
        DefaultMediaSourceFactory(dataSourceFactory(http, c)).setLoadErrorHandlingPolicy(policy)
}
