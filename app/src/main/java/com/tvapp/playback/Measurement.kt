package com.tvapp.playback

import com.tvapp.core.Clock
import com.tvapp.data.db.AppDatabase
import com.tvapp.data.db.StreamStatEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

interface StatSink { suspend fun insert(s: StreamStatEntity) }
class DbStatSink(private val db: AppDatabase) : StatSink { override suspend fun insert(s: StreamStatEntity) = db.local().insertStat(s) }

class Measurement(private val sink: StatSink, private val clock: Clock, private val scope: CoroutineScope) {
    private var url: String? = null; private var startedAt = 0L; private var firstFrameAt: Long? = null
    private var rebuffers = 0; private val bitrates = ArrayList<Int>()

    fun start(url: String) { finish(); this.url = url; startedAt = clock.elapsedMs(); firstFrameAt = null; rebuffers = 0; bitrates.clear() }
    fun firstFrame() { if (firstFrameAt == null) firstFrameAt = clock.elapsedMs() }
    fun rebuffer() { rebuffers++ }
    fun bitrate(kbps: Int) { bitrates += kbps }
    fun ttffMs(): Int? = firstFrameAt?.let { (it - startedAt).toInt() }

    fun finish() {
        val u = url ?: return
        val ff = firstFrameAt
        url = null
        if (ff == null) return // never produced a frame: the failure record covers it, no stat
        val row = StreamStatEntity(streamUrl = u, ttffMs = (ff - startedAt).toInt(), rebuffers = rebuffers,
            bitrateKbps = if (bitrates.isEmpty()) null else bitrates.sorted()[bitrates.size / 2],
            playedMs = clock.elapsedMs() - ff, recordedElapsedMs = clock.elapsedMs())
        scope.launch { sink.insert(row) }
    }

    suspend fun localScores(urls: List<String>, qualityOf: (String) -> String?, statsFor: suspend (String) -> List<StreamStatEntity>): Map<String, Double> =
        urls.mapNotNull { u -> LocalScore.compute(statsFor(u).map { StatSample(it.ttffMs, it.rebuffers, it.bitrateKbps, it.playedMs) }, qualityOf(u))?.let { u to it } }.toMap()
}
