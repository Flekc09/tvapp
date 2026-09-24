// Media3's AnalyticsListener and MediaLoadData are @UnstableApi (Global Constraints: opt in per file, lint-enforced).
@file:OptIn(UnstableApi::class)

package com.tvapp.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.Format
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.MediaLoadData
import com.tvapp.core.Clock
import com.tvapp.core.DateProvider
import com.tvapp.data.catalog.CatalogImporter
import com.tvapp.data.db.AppDatabase
import com.tvapp.data.db.RecentEntity
import com.tvapp.data.db.StreamFailureEntity
import com.tvapp.net.NetworkState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

data class PlayerState(
    val channelId: String? = null, val playing: Candidate? = null, val attempting: Candidate? = null, val notice: Notice? = null,
    val resolution: String? = null, val bitrateKbps: Int? = null, val bufferMs: Long = 0, val tuneMs: Long? = null, val paused: Boolean = false,
    /** The Channels inset's dwell tune: it pushes no card and touches neither Recent nor the previous channel until OK commits it (Opus adversarial review 2026-09-23, major 7). */
    val preview: Boolean = false,
) {
    /** A tune is in flight and no picture is up yet: Back cancels it and the banner-hide timer is not armed (Task 12). */
    val tuning: Boolean get() = attempting != null && playing == null
}

class PlayerController(
    context: Context, private val http: OkHttpClient, private val db: AppDatabase, private val importer: CatalogImporter,
    private val clock: Clock, dates: DateProvider, private val network: NetworkState, private val scope: CoroutineScope,
) {
    companion object { const val STALL_MS = 10_000L }

    val player: ExoPlayer = PlayerFactory.create(context, http)
    private val appContext = context.applicationContext
    private val engine = FailoverEngine(clock) { TuneBudget(clock) }
    private val measurement = Measurement(DbStatSink(db), clock, scope)
    private val brokenHere = BrokenHere(dates)
    private val poorSignal = PoorSignal(clock) { _state.value.playing?.quality }   // Task 15
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state
    // Read on Media3's loader thread; a StateFlow read is safe there.
    private val loadPolicy = TunePolicy { _state.value.playing == null }

    private var tickJob: Job? = null
    private var retryJob: Job? = null
    private var pollJob: Job? = null
    private var stallJob: Job? = null
    private var tuneSerial = 0L
    private var tuneStartedAt = 0L
    private val recordedThisTune = HashSet<String>()
    private var consecutiveChannelFailures = 0
    private var lastChannelFailureAt = 0L
    private var committedChannelId: String? = null   // the last channel tuned outside a preview
    private var lastFirstFrameAt: Long? = null
    private var rejoining = false // Play jumps to the live edge; the buffering that follows is not a rebuffer (Opus adversarial review 2026-09-23, minor 20)
    var previousChannelId: String? = null; private set

    init {
        player.addListener(object : Player.Listener {
            override fun onRenderedFirstFrame() = handle(PlayerEvent.FirstFrame)
            override fun onPlayerError(error: PlaybackException) {
                if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) { player.seekToDefaultPosition(); player.prepare(); return }
                handle(PlayerEvent.Error(error.errorCodeName))
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) handle(PlayerEvent.Ended)
                if (playbackState == Player.STATE_BUFFERING && _state.value.playing != null) {
                    if (!rejoining) { measurement.rebuffer(); poorSignal.onRebuffer(); checkPoorSignal(); handle(PlayerEvent.Rebuffer) }
                    armStallWatchdog()
                }
                if (playbackState == Player.STATE_READY) { stallJob?.cancel(); rejoining = false }
            }
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                _state.update { it.copy(resolution = "${videoSize.height}p") }
                poorSignal.onResolution(videoSize.height); checkPoorSignal()
            }
        })
        player.addAnalyticsListener(object : AnalyticsListener {
            override fun onDownstreamFormatChanged(eventTime: AnalyticsListener.EventTime, mediaLoadData: MediaLoadData) {
                val f: Format = mediaLoadData.trackFormat ?: return
                if (f.bitrate > 0) { measurement.bitrate(f.bitrate / 1000); _state.update { it.copy(bitrateKbps = f.bitrate / 1000) } }
            }
        })
        pollJob = scope.launch { while (isActive) { delay(1000); _state.update { it.copy(bufferMs = player.totalBufferedDuration) } } }
    }

    // ---- public API ----

    fun tune(channelId: String, manual: Candidate? = null, preview: Boolean = false) {
        val serial = ++tuneSerial
        abandon()
        val isPreview = preview && channelId != committedChannelId // resting on the committed channel again is not a preview
        if (!isPreview) commit(channelId)
        _state.update { PlayerState(channelId = channelId, preview = isPreview) }
        scope.launch {
            val ids = importer.activeImportIds()
            val rows = db.catalog().streamsForChannel(channelId, ids)
            if (serial != tuneSerial) return@launch
            val candidates = rows.map { Candidate(it.url, it.health, it.score, it.quality, it.format, it.referrer, it.userAgent) }
            val demoted = db.local().failedUrlsSince(clock.elapsedMs() - StreamQueue.DEMOTION_MS).toSet()
            val local = measurement.localScores(candidates.map { it.url }, { u -> candidates.first { it.url == u }.quality }) { db.local().statsForStream(it) }
            val ordered = if (manual != null) listOf(manual) + StreamQueue.order(candidates.filter { it.url != manual.url }, demoted, local)
                          else StreamQueue.order(candidates, demoted, local)
            if (manual != null) db.local().clearFailures(manual.url)
            if (serial != tuneSerial) return@launch
            tuneStartedAt = clock.elapsedMs()
            apply(engine.begin(ordered, manual = manual != null), null, serial)
            if (engine.attempting != null) tickJob = scope.launch { tickLoop(serial) }
        }
    }

    /** OK on the row the inset is previewing: keep the stream that is already up and make it the committed channel. False when there is no such preview. */
    fun commitPreview(channelId: String): Boolean {
        val s = _state.value
        if (s.channelId != channelId || !s.preview) return false
        if (s.attempting == null && s.playing == null && s.notice !is Notice.NotWorking) return false // a cancelled preview has nothing to keep
        commit(channelId)
        _state.update { it.copy(preview = false) } // a failed preview now shows the card, because the viewer chose it
        return true
    }

    /**
     * Abandons any tune, stops the player and clears `playing`. Used by Back during a tune, Home and standby (`MainActivity.onStop`)
     * and the sleep timer. The surface keeps its last frame (`setKeepContentOnPlayerReset`); decoders and network are released,
     * the keep-screen-on flag drops because it follows `playing`, and the sync worker's idle gate can open
     * (Opus adversarial review 2026-09-23, majors 2, 3, 4 and 16).
     */
    fun cancel() {
        ++tuneSerial; abandon()
        _state.update { it.copy(attempting = null, playing = null, notice = null) }
        scope.launch { player.stop() } // on Main like every other player call; Main.immediate runs it at once when the caller is already there
    }
    fun switchSource() { if (engine.playing != null) apply(engine.onEvent(PlayerEvent.Error("viewer switch")), null, tuneSerial) }
    fun previous() { previousChannelId?.let { tune(it) } }
    fun retuneToLive() { _state.value.channelId?.let { tune(it, preview = _state.value.preview) } }
    fun pause() { stallJob?.cancel(); player.playWhenReady = false; player.volume = 0f; _state.update { it.copy(paused = true) } }
    fun play() {
        if (!_state.value.paused) return
        rejoining = true; player.seekToDefaultPosition(); player.volume = 1f; player.playWhenReady = true
        _state.update { it.copy(paused = false, notice = Notice.BackToLive) }
        scope.launch { delay(2000); _state.update { if (it.notice == Notice.BackToLive) it.copy(notice = null) else it } }
    }
    fun release() { abandon(); pollJob?.cancel(); measurement.finish(); player.release() }

    // ---- internals ----

    // Previous channel and Recent follow committed channels only, never previews (Opus adversarial review 2026-09-23, major 7).
    private fun commit(channelId: String) {
        val prev = committedChannelId
        if (prev != null && prev != channelId) previousChannelId = prev
        committedChannelId = channelId
        scope.launch { db.local().upsertRecent(RecentEntity(channelId, System.currentTimeMillis())) }
    }

    private fun abandon() { tickJob?.cancel(); retryJob?.cancel(); stallJob?.cancel(); engine.cancel(); measurement.finish(); recordedThisTune.clear(); poorSignal.reset() }

    private suspend fun tickLoop(serial: Long) {
        while (scope.isActive && serial == tuneSerial) {
            delay(250)
            val d = engine.onTick()
            if (d !is Decision.Continue) { apply(d, null, serial); if (d is Decision.Stop) return }
        }
    }

    private fun handle(e: PlayerEvent) { apply(engine.onEvent(e), e, tuneSerial) }

    // After the first frame Media3 keeps retrying a stalled stream with no end the viewer can see; 10 s of frozen picture
    // counts as a mid-play death, so failover (or, past the restart cap, the card) takes over (Opus adversarial review 2026-09-23, major 10).
    private fun armStallWatchdog() {
        stallJob?.cancel()
        if (_state.value.paused) return
        val serial = tuneSerial
        stallJob = scope.launch {
            delay(STALL_MS)
            val st = _state.value
            if (serial == tuneSerial && !st.paused && st.playing != null && player.playbackState == Player.STATE_BUFFERING) handle(PlayerEvent.Error("stall"))
        }
    }

    private fun play(c: Candidate, notice: Notice?) {
        measurement.start(c.url); poorSignal.reset(); stallJob?.cancel()
        _state.update { it.copy(attempting = c, playing = null, notice = notice, resolution = null, bitrateKbps = null) }
        val source = PlayerFactory.mediaSourceFactory(http, c, loadPolicy).createMediaSource(PlayerFactory.mediaItem(c))
        player.setMediaSource(source); player.prepare(); player.volume = if (_state.value.paused) 0f else 1f; player.playWhenReady = !_state.value.paused
    }

    private fun apply(d: Decision, cause: PlayerEvent?, serial: Long) {
        if (serial != tuneSerial) return
        when (d) {
            Decision.Continue -> if (cause == PlayerEvent.FirstFrame) onFirstFrame()
            is Decision.Play -> { recordFailure(cause); play(d.c, d.notice) }
            is Decision.Retry -> {
                recordFailure(cause)
                _state.update { it.copy(attempting = d.c, playing = null, notice = d.notice) }
                retryJob = scope.launch { delay(d.afterMs); if (serial == tuneSerial && engine.attempting?.url == d.c.url) play(d.c, d.notice) }
            }
            is Decision.Stop -> {
                // State first, synchronously, so the card always shows; DB work afterwards in its own coroutine.
                recordFailure(cause)
                player.stop() // a slow or stalled source would keep loading behind the card
                consecutiveChannelFailures++; lastChannelFailureAt = clock.elapsedMs()
                val ch = _state.value.channelId
                _state.update { it.copy(notice = d.notice, attempting = null, playing = null) }
                val guard = outageGuard()
                if (ch != null) scope.launch { if (networkUsable() && !guard) db.local().upsertChannelStatus(brokenHere.afterFailure(db.local().channelStatus(ch), ch)) }
                tickJob?.cancel(); stallJob?.cancel()
            }
        }
    }

    private fun onFirstFrame() {
        measurement.firstFrame(); lastFirstFrameAt = clock.elapsedMs()
        val c = engine.playing ?: return
        val ch = _state.value.channelId
        consecutiveChannelFailures = 0
        _state.update { it.copy(playing = c, attempting = null, notice = if (it.notice is Notice.Trying || it.notice == Notice.Switching) null else it.notice, tuneMs = clock.elapsedMs() - tuneStartedAt) }
        if (ch != null) scope.launch { db.local().deleteChannelStatus(ch) }
    }

    // VALIDATED never arrives where Google's connectivity check is blocked (DNS filtering); a picture in the last 10 minutes proves the network works (Opus adversarial review 2026-09-23, minor 22).
    private fun networkUsable(): Boolean = network.isValidated() || (network.hasInternet() && lastFirstFrameAt?.let { clock.elapsedMs() - it < 10 * 60_000 } == true)

    private fun outageGuard(): Boolean = consecutiveChannelFailures >= 3 && clock.elapsedMs() - lastChannelFailureAt < 60_000

    private fun recordFailure(cause: PlayerEvent?) {
        if (cause !is PlayerEvent.Error && cause != PlayerEvent.Ended) return
        if (cause is PlayerEvent.Error && cause.message == "viewer switch") return
        val url = engine.lastFailedUrl ?: return
        if (url in recordedThisTune) return
        if (!networkUsable() || outageGuard()) return
        recordedThisTune += url
        val row = StreamFailureEntity(streamUrl = url, failedElapsedMs = clock.elapsedMs())
        scope.launch { db.local().insertFailure(row) }
    }

    private fun checkPoorSignal() {
        val ch = _state.value.channelId ?: return
        if (_state.value.playing == null || !poorSignal.shouldPrompt(ch)) return
        if (autoSwitch) { switchSource(); return }
        _state.update { it.copy(notice = Notice.PoorSignal) }
        scope.launch { delay(8000); _state.update { if (it.notice == Notice.PoorSignal) it.copy(notice = null) else it } }
    }
    @Volatile var autoSwitch: Boolean = false   // set from the Advanced setting (Task 15)
}
