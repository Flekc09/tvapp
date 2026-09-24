package com.tvapp.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tvapp.AppContainer
import com.tvapp.data.catalog.CatalogImporter
import com.tvapp.data.db.ChannelEntity
import com.tvapp.data.db.FavoriteEntity
import com.tvapp.data.db.SettingEntity
import com.tvapp.data.db.StreamEntity
import com.tvapp.playback.Candidate
import com.tvapp.playback.Notice
import com.tvapp.playback.StreamQueue
import com.tvapp.playback.TuneDebouncer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ListFilter(val kind: Kind, val country: String? = null, val category: String? = null) {
    enum class Kind { ALL, COUNTRY, CATEGORY, FAVORITES, RECENT }
    fun encode() = "${kind.name}|${country ?: ""}|${category ?: ""}"
    companion object { fun decode(s: String?): ListFilter? = s?.split('|')?.takeIf { it.size == 3 }?.let { ListFilter(Kind.valueOf(it[0]), it[1].ifEmpty { null }, it[2].ifEmpty { null }) } }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(private val c: AppContainer) : ViewModel() {
    private val db = c.db; private val local = db.local(); private val catalog = db.catalog()
    val player = c.playerController

    private val _overlays = MutableStateFlow<List<Overlay>>(emptyList())
    val overlays: StateFlow<List<Overlay>> = _overlays
    val top: Overlay get() = _overlays.value.lastOrNull() ?: Overlay.NONE
    fun push(o: Overlay) { _overlays.update { it + o }; if (o == Overlay.STRIP) armStrip() }
    fun pop(): Boolean { if (_overlays.value.isEmpty()) return false; if (top == Overlay.CHANNELS) keepPreview(); _overlays.update { it.dropLast(1) }; return true }
    fun closeAll(keepPreview: Boolean = true) { if (keepPreview && Overlay.CHANNELS in _overlays.value) keepPreview(); _overlays.value = emptyList() }
    // Leaving Channels keeps whatever the inset shows (spec 5.4), so a preview becomes the committed channel: it gets the card,
    // Recent and Previous from then on (Opus adversarial review 2026-09-23, major 7).
    private fun keepPreview() {
        val s = player.state.value; val id = s.channelId ?: return
        if (s.preview && player.commitPreview(id)) viewModelScope.launch { _banner.value = catalog.channel(id, c.importer.activeImportIds()) }
    }
    private val playerPanels = setOf(Overlay.STRIP, Overlay.CONTEXT_MENU, Overlay.SOURCES, Overlay.NOT_WORKING)
    private fun closePlayerPanels() { _overlays.update { o -> o.filterNot { it in playerPanels } } }

    private val _filter = MutableStateFlow(ListFilter(ListFilter.Kind.FAVORITES))
    val filter: StateFlow<ListFilter> = _filter
    fun setFilter(f: ListFilter) { _filter.value = f; viewModelScope.launch { local.setSetting(SettingEntity("list_filter", f.encode())) } }

    private fun boolSetting(key: String) = local.settingFlow(key).map { it == "true" }
    val showAdult = boolSetting("show_adult"); val showNoUp = boolSetting("show_no_up"); val showBrokenHere = boolSetting("show_broken_here")
    // The active import id is a query parameter like the filter, so a catalog flip (idle sync or "Refresh channel list now")
    // re-runs every list against the new rows (Opus adversarial review 2026-09-23, major 1).
    private val activeIds = local.settingFlow(CatalogImporter.KEY_ACTIVE).map { listOfNotNull(it?.toLongOrNull()) }.distinctUntilChanged()
    private val flags = combine(showAdult, showNoUp, showBrokenHere) { a, n, b -> Triple(a, n, b) }

    val visibleChannels: StateFlow<List<ChannelEntity>> = channelsOf(_filter).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** The list for any filter under the same settings as visibleChannels: the Channels column previews an entry with it before it is committed (Task 13). */
    fun channelsOf(filter: Flow<ListFilter>): Flow<List<ChannelEntity>> = combine(filter, flags, local.brokenHereIdsFlow(), activeIds) { f, (adult, noUp, broken), brokenIds, ids ->
        Params(f, adult, noUp, if (broken) emptyList() else brokenIds, ids)
    }.flatMapLatest { p -> channelsFor(p) }

    /** Channels marked broken here, for the rows' status word (Task 13). */
    val brokenIds: StateFlow<Set<String>> = local.brokenHereIdsFlow().map { it.toSet() }.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** The adult / no-up / broken-here arguments every count shares with channelList, so a count never disagrees with its list. */
    private suspend fun countArgs(): Triple<Boolean, Boolean, List<String>> {
        val broken = if (local.setting("show_broken_here") == "true") emptyList() else local.brokenHereIds()
        return Triple(local.setting("show_adult") == "true", local.setting("show_no_up") == "true", broken)
    }
    /** Visible channels per country, as its "All categories" list counts them (Browse, the Channels column). */
    suspend fun countryCounts(): Map<String?, Int> { val (a, n, h) = countArgs(); return catalog.countryCounts(c.importer.activeImportIds(), a, n, h).associate { it.country to it.n } }
    /** Visible channels per category within a country (null: worldwide), counted in Kotlin from the '|'-joined strings. */
    suspend fun categoryCounts(country: String?): Map<String, Int> {
        val (a, n, h) = countArgs()
        return catalog.categoryStrings(c.importer.activeImportIds(), country, a, n, h).flatMap { it.split('|') }.filter { it.isNotEmpty() }.groupingBy { it }.eachCount()
    }
    private data class Params(val f: ListFilter, val adult: Boolean, val noUp: Boolean, val hidden: List<String>, val ids: List<Long>)

    companion object { const val SOURCE_MISSING = "missing" } // placeholder rows for favorites no longer in the catalog

    private fun channelsFor(p: Params): Flow<List<ChannelEntity>> = when (p.f.kind) {
        ListFilter.Kind.ALL -> catalog.channelList(p.ids, null, null, p.adult, p.noUp, p.hidden)
        ListFilter.Kind.COUNTRY -> catalog.channelList(p.ids, p.f.country, p.f.category, p.adult, p.noUp, p.hidden)
        ListFilter.Kind.CATEGORY -> catalog.channelList(p.ids, p.f.country, p.f.category, p.adult, p.noUp, p.hidden)
        ListFilter.Kind.FAVORITES -> local.favorites().map { favs -> shownFavorites(favs, p.ids, p.adult) }
        ListFilter.Kind.RECENT -> local.recents().map { rs ->
            val m = catalog.channelsByIds(p.ids, rs.map { it.channelId }).associateBy { it.id }
            rs.mapNotNull { r -> m[r.channelId]?.takeIf { p.adult || !it.adult } }
        }
    }.filter { list -> list.isNotEmpty() || c.importer.activeImportIds() == p.ids }
    // The flip deletes the old rows, so Room re-runs the old query a moment before the new id arrives; that empty result is dropped so lists never go empty (spec 5.2).

    /**
     * Favorites in position order. A favorite that left the catalog is a placeholder that keeps its place; an adult favorite is left out
     * while adult channels are hidden (Opus adversarial review 2026-09-23, major 8). Row numbers and digit keys both count this list.
     */
    private suspend fun shownFavorites(favs: List<FavoriteEntity>, ids: List<Long>, showAdult: Boolean): List<ChannelEntity> {
        val m = catalog.channelsByIds(ids, favs.map { it.channelId }).associateBy { it.id }
        return favs.mapNotNull { f ->
            val ch = m[f.channelId]
            when {
                ch == null -> ChannelEntity(f.channelId, f.name.ifEmpty { f.channelId }, "", null, null, "", null, null, false, false, SOURCE_MISSING, -1)
                ch.adult && !showAdult -> null
                else -> ch
            }
        }
    }
    private suspend fun adultShown() = local.setting("show_adult") == "true"
    /** False for an adult channel while adult channels are hidden: startup, digits and previous never play one (Opus review M8). */
    suspend fun allowed(id: String): Boolean = adultShown() || catalog.channel(id, c.importer.activeImportIds())?.adult != true

    private val _banner = MutableStateFlow<ChannelEntity?>(null)
    val bannerChannel: StateFlow<ChannelEntity?> = _banner
    private val _bannerVisible = MutableStateFlow(false)
    val bannerVisible: StateFlow<Boolean> = _bannerVisible
    private var bannerJob: Job? = null
    val bannerPosition: StateFlow<Pair<Int, Int>?> = combine(_banner, visibleChannels) { b, list -> b?.let { ch -> list.indexOfFirst { it.id == ch.id }.takeIf { it >= 0 }?.let { it + 1 to list.size } } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val debouncer = TuneDebouncer(viewModelScope, 300) { id -> player.tune(id) }

    fun showBanner() { _bannerVisible.value = true; armBannerHide() }
    // The banner hides 3 s after video is up (spec 6), not 3 s after the keypress: while a tune is in flight the timer is not armed, and the first frame arms it.
    private fun armBannerHide() { bannerJob?.cancel(); if (player.state.value.tuning) return; bannerJob = viewModelScope.launch { delay(3000); _bannerVisible.value = false } }
    init { viewModelScope.launch { player.state.map { it.playing != null }.distinctUntilChanged().collect { if (it) armBannerHide() } } }

    fun surf(delta: Int) {
        closePlayerPanels()
        val list = visibleChannels.value; if (list.isEmpty()) { showBanner(); return } // flow map red route 2A: the banner shows the list name so the viewer sees why nothing moved
        val currentId = _banner.value?.id ?: player.state.value.channelId
        val idx = list.indexOfFirst { it.id == currentId }.let { if (it < 0) 0 else (it + delta).mod(list.size) }
        val target = list[idx]
        _banner.value = target; showBanner()
        debouncer.request(target.id)
        viewModelScope.launch { c.prefetcher.neighbors(candidateOf(list[(idx - 1).mod(list.size)]), candidateOf(list[(idx + 1).mod(list.size)])) }
    }

    fun select(channelId: String) { viewModelScope.launch {
        _banner.value = catalog.channel(channelId, c.importer.activeImportIds()); closeAll(keepPreview = false); showBanner()
        if (!player.commitPreview(channelId)) player.tune(channelId) // OK on the row the inset is already previewing keeps that stream up
    } }
    fun previous() {
        val id = player.previousChannelId ?: return
        viewModelScope.launch {
            if (!allowed(id)) { showBanner(); return@launch }
            _banner.value = catalog.channel(id, c.importer.activeImportIds()); showBanner(); player.previous()
        }
    }
    fun favoriteByNumber(n: Int) { viewModelScope.launch {
        val ch = shownFavorites(local.favoritesNow(), c.importer.activeImportIds(), adultShown()).getOrNull(n - 1)
        if (ch != null && ch.source != SOURCE_MISSING) select(ch.id) else showBanner()
    } }
    fun toggleFavorite(id: String) { viewModelScope.launch {
        val favs = local.favoritesNow()
        if (favs.any { it.channelId == id }) { local.deleteFavorite(id); toast("Removed from favorites") }
        else { local.upsertFavorite(FavoriteEntity(id, favs.size, catalog.channel(id, c.importer.activeImportIds())?.name ?: "")); toast("Added to favorites") }
    } }
    /** Country and category names by id for the banner's region row and list name; re-read on every catalog flip. */
    val countries: StateFlow<Map<String, com.tvapp.data.db.CountryEntity>> = activeIds.mapLatest { ids -> catalog.countries(ids).associateBy { it.code } }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    val categoryNames: StateFlow<Map<String, String>> = activeIds.mapLatest { ids -> catalog.categories(ids).associate { it.id to it.name } }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    /** "Previous: <name>" on the banner, absent when there is no previous channel or it is an adult channel while those are hidden. */
    suspend fun previousName(): String? = player.previousChannelId?.takeIf { allowed(it) }?.let { catalog.channel(it, c.importer.activeImportIds())?.name }

    val favoriteIds: StateFlow<Set<String>> = local.favorites().map { f -> f.map { it.channelId }.toSet() }.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    /** The current channel's streams in catalog order, for the Sources menu. */
    suspend fun sourcesFor(channelId: String): List<StreamEntity> = catalog.streamsForChannel(channelId, c.importer.activeImportIds())
    fun clearFocusFirstRow() { _focusFirstRow.value = false }

    // STRIP closes 5 s after the last key; every routed key while it is open re-arms the timer (Interfaces: KeyRouter, stripJob).
    private var stripJob: Job? = null
    private fun armStrip() { stripJob?.cancel(); if (top != Overlay.STRIP) return; stripJob = viewModelScope.launch { delay(5000); _overlays.update { it - Overlay.STRIP } } }

    fun playPause() { if (player.state.value.paused) player.play() else player.pause() }
    fun okOnPlayer() { if (player.state.value.notice == Notice.PoorSignal) player.switchSource() else showBanner() }

    // One line at the top of the frame for 3 s; a new toast replaces the current one (Interfaces: toast). Not in the plan's code block; added 2026-09-24.
    private val _toast = MutableStateFlow<String?>(null); val toast: StateFlow<String?> = _toast
    private var toastJob: Job? = null
    fun toast(text: String) { toastJob?.cancel(); _toast.value = text; toastJob = viewModelScope.launch { delay(3000); _toast.value = null } }

    private val _focusFirstRow = MutableStateFlow(false); val focusFirstRow: StateFlow<Boolean> = _focusFirstRow
    fun openFavorites() { closeAll(); setFilter(ListFilter(ListFilter.Kind.FAVORITES)); _focusFirstRow.value = true; push(Overlay.CHANNELS) }

    /** Set once a held Back has opened Favorites, so the rest of that press does nothing else. MainActivity and Search's pre-keyboard interceptor share it. */
    var holdBackHandled = false
    /** A Back DOWN: the first one of a press only resets the hold, a repeat is the hold and opens Favorites once. True when the event was the hold. */
    fun onBackDown(repeatCount: Int): Boolean {
        if (repeatCount == 0) { holdBackHandled = false; return false }
        if (!holdBackHandled) { holdBackHandled = true; openFavorites() }
        return true
    }

    private val _exitArmed = MutableStateFlow(false); val exitArmed: StateFlow<Boolean> = _exitArmed
    fun back(exit: () -> Unit) {
        // Spec 5.5: Back cancels an in-flight tune on the player. Inside a list overlay it pops instead, and an in-flight preview carries on (spec 5.4).
        if (player.state.value.attempting != null && (top == Overlay.NONE || top == Overlay.NOT_WORKING)) { player.cancel(); return }
        if (top == Overlay.NOT_WORKING) { pop(); showBanner(); return } // the card closes to the banner, whose status row reads Not working (key table; Opus adversarial review 2026-09-23, minor 21)
        if (pop()) return
        if (_exitArmed.value) { exit(); return }
        _exitArmed.value = true; viewModelScope.launch { delay(2000); _exitArmed.value = false }
    }

    fun focusInList(ch: ChannelEntity?) { viewModelScope.launch { c.prefetcher.focus(candidateOf(ch)) } }

    // Warm the stream the queue will most likely try first: StreamQueue's health-before-score order, without the tune's demotion and local-score inputs (Opus adversarial review 2026-09-23, major 18).
    private suspend fun candidateOf(ch: ChannelEntity?): Candidate? {
        if (ch == null || ch.source == SOURCE_MISSING) return null
        val cands = catalog.streamsForChannel(ch.id, c.importer.activeImportIds()).map { Candidate(it.url, it.health, it.score, it.quality, it.format, it.referrer, it.userAgent) }
        return StreamQueue.order(cands, emptySet(), emptyMap()).firstOrNull()
    }

    init {
        viewModelScope.launch { ListFilter.decode(local.setting("list_filter"))?.let { _filter.value = it } }
        viewModelScope.launch {
            // Card sync. A failed preview only marks the details panel; the card is for a channel the viewer chose (Opus adversarial review 2026-09-23, major 7).
            // Only on a change: a card the viewer closed or covered is not pushed again while the same notice stands.
            player.state.map { it.notice is Notice.NotWorking && !it.preview }.distinctUntilChanged().collect { cardDue ->
                if (cardDue && top != Overlay.NOT_WORKING) push(Overlay.NOT_WORKING)
                if (!cardDue && Overlay.NOT_WORKING in _overlays.value) _overlays.update { it - Overlay.NOT_WORKING }
            }
        }
        viewModelScope.launch {
            // Idle: nothing playing for 10 minutes -> allow a catalog sync. The worker re-checks the gate itself.
            player.state.map { it.playing == null }.distinctUntilChanged().collectLatest { idle ->
                if (idle) {
                    local.setSetting(SettingEntity(com.tvapp.data.catalog.CatalogSync.KEY_LAST_PLAYING, System.currentTimeMillis().toString()))
                    delay(com.tvapp.data.catalog.CatalogSync.IDLE_MS); com.tvapp.data.catalog.CatalogSync.runWhenIdle(c.application)
                }
            }
        }
    }

    private val _sleeping = MutableStateFlow(false)
    /** True from the moment the sleep timer fires until the next key: the Channels inset does not preview while nobody is watching. */
    val sleeping: StateFlow<Boolean> = _sleeping
    fun wake() { _sleeping.value = false; armStrip() } // called by MainActivity on every key
    private val _sleepEndsAt = MutableStateFlow<Long?>(null); val sleepEndsAt: StateFlow<Long?> = _sleepEndsAt
    private var sleepJob: Job? = null
    fun setSleepTimer(minutes: Int?) {
        sleepJob?.cancel()
        _sleepEndsAt.value = minutes?.let { c.clock.elapsedMs() + it * 60_000L }
        sleepJob = minutes?.let { m -> viewModelScope.launch { delay(m * 60_000L); sleepNow() } }
    }
    /** The sleep timer firing: playback stops (bandwidth and keep-screen-on go with it) and Channels opens over the last frame (Opus adversarial review 2026-09-23, major 3). */
    fun sleepNow() { _sleepEndsAt.value = null; _sleeping.value = true; player.cancel(); closeAll(keepPreview = false); push(Overlay.CHANNELS) }
}
