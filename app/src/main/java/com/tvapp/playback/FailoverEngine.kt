package com.tvapp.playback

import com.tvapp.core.Clock

sealed class PlayerEvent { object FirstFrame : PlayerEvent(); data class Error(val message: String) : PlayerEvent(); object Ended : PlayerEvent(); object Rebuffer : PlayerEvent() }
sealed class Notice {
    data class Trying(val index: Int, val total: Int) : Notice(); object Switching : Notice(); object BackToLive : Notice()
    data class NotWorking(val tried: Int) : Notice(); object PoorSignal : Notice()
}
sealed class Decision {
    data class Play(val c: Candidate, val notice: Notice?) : Decision()
    data class Retry(val c: Candidate, val afterMs: Long, val notice: Notice?) : Decision()
    data class Stop(val notice: Notice) : Decision()
    object Continue : Decision()
}

class FailoverEngine(private val clock: Clock, private val budgetFactory: () -> TuneBudget) {
    companion object {
        const val RETRY_GAP_MS = 2000L
        // A channel whose streams keep dying mid-play gets the card instead of cycling forever (Opus adversarial review 2026-09-23, major 10).
        const val MAX_MIDPLAY_DEATHS = 3
        const val MIDPLAY_WINDOW_MS = 120_000L
    }

    private var queue: List<Candidate> = emptyList()
    private var manual = false
    private var budget: TuneBudget? = null
    private var current: Candidate? = null
    private var currentStartedAt = 0L
    private var hadFirstFrame = false
    private var active = false
    private val tried = LinkedHashSet<String>()          // this tune
    private val worked = LinkedHashSet<String>()         // produced a frame since begin()
    private val lastDeath = HashMap<String, Long>()      // elapsed ms of last mid-play death
    private val ended = HashSet<String>()                // reached Ended since begin(): not live, never picked again for this channel
    private val midPlayDeaths = ArrayDeque<Long>()       // elapsed ms of the mid-play deaths inside the window
    val failedThisTune = LinkedHashSet<String>()
    val slowThisTune = LinkedHashSet<String>()
    var lastFailedUrl: String? = null; private set   // survives newTune(); the controller records failures from it

    val playing: Candidate? get() = if (active && hadFirstFrame) current else null
    val attempting: Candidate? get() = if (active) current else null

    fun begin(queue: List<Candidate>, manual: Boolean = false): Decision {
        this.queue = queue; this.manual = manual; active = true
        worked.clear(); lastDeath.clear(); ended.clear(); midPlayDeaths.clear(); lastFailedUrl = null
        return newTune(null, excludeFirst = null)
    }

    fun cancel() { active = false; current = null; hadFirstFrame = false; tried.clear(); failedThisTune.clear(); slowThisTune.clear(); lastFailedUrl = null }

    // A fresh budget. `excludeFirst` is the stream that just died mid-play: it is marked tried so it comes after the untried ones.
    private fun newTune(notice: Notice?, excludeFirst: String?): Decision {
        tried.clear(); failedThisTune.clear(); slowThisTune.clear()
        excludeFirst?.let { tried += it }
        budget = budgetFactory().also { it.start() }
        return startNext(notice)
    }

    private fun stop(): Decision { active = false; current = null; hadFirstFrame = false; return Decision.Stop(Notice.NotWorking(tried.size)) }

    private fun noticeFor(c: Candidate, incoming: Notice?): Notice? = when {
        incoming is Notice.Switching -> incoming
        queue.size > 1 -> Notice.Trying(queue.indexOfFirst { it.url == c.url } + 1, queue.size)
        else -> null
    }

    private fun startNext(incoming: Notice?): Decision {
        val b = budget ?: return Decision.Continue
        if (b.exhausted()) return stop()
        val untried = queue.firstOrNull { it.url !in tried && it.url !in ended }
        // After the untried ones: streams that have produced a frame before and have not failed in this tune, oldest death first.
        val next: Candidate = untried
            ?: queue.filter { it.url in worked && it.url !in failedThisTune && it.url !in ended }.minByOrNull { lastDeath[it.url] ?: 0L }
            ?: return stop()
        current = next; tried += next.url; hadFirstFrame = false; currentStartedAt = clock.elapsedMs()
        val notice = noticeFor(next, incoming)
        val sinceDeath = lastDeath[next.url]?.let { clock.elapsedMs() - it }
        return if (sinceDeath != null && sinceDeath < RETRY_GAP_MS) Decision.Retry(next, RETRY_GAP_MS - sinceDeath, notice) else Decision.Play(next, notice)
    }

    fun onEvent(e: PlayerEvent): Decision {
        if (!active) return Decision.Continue
        val c = current ?: return Decision.Continue
        return when (e) {
            PlayerEvent.FirstFrame -> { hadFirstFrame = true; worked += c.url; Decision.Continue }
            PlayerEvent.Rebuffer -> Decision.Continue
            is PlayerEvent.Error, PlayerEvent.Ended -> {
                lastFailedUrl = c.url
                manual = false
                if (e == PlayerEvent.Ended) ended += c.url
                if (hadFirstFrame) midPlayDeath(c, counted = !(e is PlayerEvent.Error && e.message == "viewer switch")) // an accepted poor-signal prompt is not a death
                else { failedThisTune += c.url; startNext(null) }
            }
        }
    }

    private fun midPlayDeath(c: Candidate, counted: Boolean): Decision {
        val now = clock.elapsedMs()
        lastDeath[c.url] = now
        if (!counted) return newTune(Notice.Switching, excludeFirst = c.url)
        midPlayDeaths.addLast(now)
        while (now - midPlayDeaths.first() >= MIDPLAY_WINDOW_MS) midPlayDeaths.removeFirst()
        if (midPlayDeaths.size > MAX_MIDPLAY_DEATHS) { active = false; current = null; hadFirstFrame = false; return Decision.Stop(Notice.NotWorking(queue.size)) }
        return newTune(Notice.Switching, excludeFirst = c.url)
    }

    fun onTick(): Decision {
        if (!active) return Decision.Continue
        val c = current ?: return Decision.Continue
        if (hadFirstFrame) return Decision.Continue
        val b = budget ?: return Decision.Continue
        if (b.exhausted()) return stop()
        val remainingAfter = queue.count { it.url !in tried && it.url !in ended }
        // A manual pick suppresses automatic failover for that attempt (spec 5.5): no 4 s cutoff, only the budget (Opus adversarial review 2026-09-23, minor 15).
        if (manual || clock.elapsedMs() - currentStartedAt < b.cutoffMs(remainingAfter)) return Decision.Continue
        slowThisTune += c.url
        return startNext(null)
    }
}
