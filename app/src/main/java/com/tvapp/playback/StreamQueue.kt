package com.tvapp.playback

data class Candidate(val url: String, val health: String, val catalogScore: Double, val quality: String?, val format: String, val referrer: String?, val userAgent: String?)
data class StatSample(val ttffMs: Int?, val rebuffers: Int, val bitrateKbps: Int?, val playedMs: Long)

object LocalScore {
    const val MIN_SAMPLES = 3
    private fun expectedKbps(q: String?): Int = when (Regex("""(\d{3,4})""").find(q ?: "")?.value?.toIntOrNull() ?: 0) {
        in 1080..9999 -> 5000; in 720..1079 -> 3000; in 576..719 -> 2000; in 480..575 -> 1500; in 1..479 -> 800; else -> 2000 }
    private fun median(xs: List<Double>): Double { val s = xs.sorted(); return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2 }

    fun compute(samples: List<StatSample>, declaredQuality: String?): Double? {
        if (samples.size < MIN_SAMPLES) return null
        val ttffs = samples.mapNotNull { it.ttffMs?.toDouble() }
        val ttffScore = if (ttffs.isEmpty()) 0.5 else 1 - minOf(median(ttffs), 5000.0) / 5000.0
        val hours = samples.sumOf { it.playedMs } / 3_600_000.0
        val perHour = if (hours <= 0) 0.0 else samples.sumOf { it.rebuffers } / hours
        val rebufferScore = 1 - minOf(perHour, 10.0) / 10.0
        val kbps = samples.mapNotNull { it.bitrateKbps?.toDouble() }
        val qualityScore = if (kbps.isEmpty()) 1.0 else minOf(median(kbps) / expectedKbps(declaredQuality), 1.0)
        return 0.4 * ttffScore + 0.4 * rebufferScore + 0.2 * qualityScore
    }
}

object StreamQueue {
    const val DEMOTION_MS = 3_600_000L
    fun order(candidates: List<Candidate>, demotedUrls: Set<String>, localScores: Map<String, Double>): List<Candidate> =
        candidates.sortedWith(compareBy<Candidate> { if (it.url in demotedUrls) 1 else 0 }
            .thenBy { if (it.health != "up") 1 else 0 }
            .thenByDescending { localScores[it.url] ?: it.catalogScore })
}
