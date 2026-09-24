package com.tvapp.ui.channels

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tvapp.data.db.ChannelEntity
import com.tvapp.playback.Notice
import com.tvapp.ui.JumpByLetter
import com.tvapp.ui.Tok
import com.tvapp.ui.player.Logo
import com.tvapp.ui.requestWhenReady
import com.tvapp.ui.player.listLine
import com.tvapp.ui.rememberInteraction
import com.tvapp.ui.state.AppViewModel
import com.tvapp.ui.state.ListFilter
import com.tvapp.ui.state.Overlay
import com.tvapp.ui.state.PreviewController
import com.tvapp.ui.state.deviceCountry
import com.tvapp.ui.tvFocus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

private data class Entry(val label: String, val count: Int?, val filter: ListFilter? = null, val opens: Overlay? = null)

/**
 * Channels overlay (docs/ui/screens/02-channels.md): collection column · list · (the root's inset) · details panel.
 * Resting on a column entry previews it; OK or Right commits it with setFilter; Back or leaving restores the committed filter.
 * Resting on a row for 700 ms tunes the inset to it as a preview tune; OK goes full screen.
 */
@Composable
fun ChannelsOverlay(vm: AppViewModel, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val committed by vm.filter.collectAsState()
    var preview by remember { mutableStateOf(committed) }
    LaunchedEffect(committed) { preview = committed }
    val visible by vm.visibleChannels.collectAsState()
    val previewed by remember(preview) { vm.channelsOf(flowOf(preview)) }.collectAsState(initial = emptyList())
    val channels = if (preview == committed) visible else previewed
    val favorites by remember { vm.channelsOf(flowOf(ListFilter(ListFilter.Kind.FAVORITES))) }.collectAsState(initial = emptyList())
    val recents by remember { vm.channelsOf(flowOf(ListFilter(ListFilter.Kind.RECENT))) }.collectAsState(initial = emptyList())
    val countries by vm.countries.collectAsState()
    val categories by vm.categoryNames.collectAsState()
    val broken by vm.brokenIds.collectAsState()
    val sleeping by vm.sleeping.collectAsState()
    val state by vm.player.state.collectAsState()
    var counts by remember { mutableStateOf<Map<String?, Int>>(emptyMap()) }
    LaunchedEffect(Unit) { counts = vm.countryCounts() }

    val preview700 = remember { PreviewController(scope, 700, vm.player.state.value.channelId) { id -> vm.player.tune(id, preview = true) } }
    DisposableEffect(Unit) { onDispose { preview700.stop() } }

    // Column: Favorites · Recent · device country · the current filter when it is none of those · All · Browse › Search › Settings ›
    val dc = deviceCountry()
    val entries = buildList {
        add(Entry("Favorites", favorites.size, ListFilter(ListFilter.Kind.FAVORITES)))
        add(Entry("Recent", recents.size, ListFilter(ListFilter.Kind.RECENT)))
        add(Entry(countries[dc]?.name ?: dc, counts[dc], ListFilter(ListFilter.Kind.COUNTRY, dc)))
        val pinned = setOf(ListFilter(ListFilter.Kind.FAVORITES), ListFilter(ListFilter.Kind.RECENT), ListFilter(ListFilter.Kind.COUNTRY, dc), ListFilter(ListFilter.Kind.ALL))
        if (committed !in pinned) add(Entry(listLine(committed, null, countries, categories), visible.size, committed))
        add(Entry("All", counts.values.sum().takeIf { counts.isNotEmpty() }, ListFilter(ListFilter.Kind.ALL)))
        add(Entry("Browse", null, opens = Overlay.BROWSE)); add(Entry("Search", null, opens = Overlay.SEARCH)); add(Entry("Settings", null, opens = Overlay.SETTINGS))
    }
    val selectedEntry = entries.indexOfFirst { it.filter == committed }.coerceAtLeast(0)
    val columnFocus = remember { FocusRequester() }
    val rowFocus = remember { FocusRequester() }
    var inColumn by remember { mutableStateOf(false) }
    val list = rememberLazyListState()
    var target by remember { mutableIntStateOf(-1) }       // the row that holds (or gets) focus
    var focused by remember { mutableStateOf<ChannelEntity?>(null) }

    // Back from the column with an uncommitted preview restores the committed filter first (review major 11).
    BackHandler(enabled = inColumn && preview != committed) { preview = committed }

    var focusedIndex by remember { mutableIntStateOf(-1) }
    var columnHasFocus by remember { mutableStateOf(false) }
    fun focusRow(i: Int) { target = i; scope.launch { list.scrollToItem((i - 3).coerceAtLeast(0)); rowFocus.requestWhenReady { focusedIndex == i } } }
    fun commit(f: ListFilter) { vm.setFilter(f); preview = f }

    // Opening: favorite 1 after hold Back, else the current channel's row, else the first; an empty list puts focus in the column.
    LaunchedEffect(channels.isNotEmpty(), preview) {
        if (inColumn) return@LaunchedEffect
        if (channels.isEmpty()) { columnFocus.requestWhenReady { columnHasFocus }; return@LaunchedEffect }
        val first = vm.focusFirstRow.value.also { if (it) vm.clearFocusFirstRow() }
        focusRow(if (first) 0 else channels.indexOfFirst { it.id == state.channelId }.coerceAtLeast(0))
    }

    Row(modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        // Collection column, on its own panel.
        Column(Modifier.width(224.dp).fillMaxHeight().background(Tok.panel, Tok.radiusMd).padding(vertical = 16.dp, horizontal = 8.dp)) {
            Text("Channels", style = Tok.lg.copy(fontSize = 32.sp, lineHeight = 40.sp), modifier = Modifier.padding(start = 8.dp, bottom = 8.dp))
            entries.forEachIndexed { idx, e ->
                if (e.opens == Overlay.BROWSE) HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Color(0x1FFFFFFF))
                val i = rememberInteraction()
                var restJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
                Row(
                    Modifier.fillMaxWidth().height(48.dp)
                        .then(if (idx == selectedEntry) Modifier.focusRequester(columnFocus) else Modifier)
                        .focusProperties { right = if (channels.isNotEmpty()) rowFocus else FocusRequester.Default }
                        .onFocusChanged { f ->
                            if (idx == selectedEntry) columnHasFocus = f.isFocused
                            if (f.isFocused) { inColumn = true; focusedIndex = -1; restJob?.cancel(); e.filter?.let { flt -> restJob = scope.launch { delay(150); preview = flt } } }
                        }
                        .onPreviewKeyEvent { k ->
                            // Right on a collection commits it and returns to the list (§2).
                            if (k.type == KeyEventType.KeyDown && k.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && e.filter != null) { commit(e.filter); inColumn = false; focusRow(0); true } else false
                        }
                        .tvFocus(i)
                        .clickable(interactionSource = i, indication = null) {
                            when { e.filter != null -> { commit(e.filter); inColumn = false; focusRow(0) }; e.opens != null -> vm.push(e.opens) }
                        }
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val selected = idx == selectedEntry
                    if (selected) Box(Modifier.width(3.dp).height(24.dp).background(Tok.accent))
                    Spacer(Modifier.width(if (selected) 5.dp else 8.dp))
                    Text(e.label, style = Tok.md.copy(color = if (selected) Tok.accent else Tok.text), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    e.count?.let { Text("%,d".format(it), style = Tok.sm.copy(color = Tok.muted)) }
                    if (e.opens != null) Text("›", style = Tok.md.copy(color = Tok.muted))
                }
            }
        }

        // List panel.
        Box(Modifier.width(320.dp).fillMaxHeight().background(Tok.panel, Tok.radiusMd).padding(16.dp)) {
            if (channels.isEmpty()) EmptyList(preview, countries[preview.country]?.name ?: preview.country)
            else LazyColumn(
                state = list,
                modifier = Modifier.onPreviewKeyEvent { k ->
                    val d = JumpByLetter.digitOf(k.nativeKeyEvent.keyCode) ?: return@onPreviewKeyEvent false
                    if (k.type == KeyEventType.KeyDown) {
                        JumpByLetter.next(channels.map { it.name }, target.coerceAtLeast(0), d)?.let { focusRow(it) }
                        vm.toast(JumpByLetter.label(d))
                    }
                    true
                },
            ) {
                itemsIndexed(channels, key = { _, c -> c.id }) { idx, ch ->
                    val favoritesList = preview.kind == ListFilter.Kind.FAVORITES
                    ChannelRow(ch, if (favoritesList) idx + 1 else null, broken,
                        modifier = Modifier
                            .then(if (idx == target) Modifier.focusRequester(rowFocus) else Modifier)
                            .focusProperties { left = columnFocus }
                            .onFocusChanged { f ->
                                if (!f.isFocused) { if (focusedIndex == idx) focusedIndex = -1; return@onFocusChanged }
                                focusedIndex = idx
                                if (inColumn && preview != committed) commit(preview) // reaching the list from the column commits what it shows
                                inColumn = false; target = idx; focused = ch
                                vm.focusInList(ch)
                                if (!sleeping && ch.source != AppViewModel.SOURCE_MISSING) preview700.focus(ch.id)
                            },
                        onClick = { if (ch.source != AppViewModel.SOURCE_MISSING) vm.select(ch.id) },
                        onLongClick = { vm.toggleFavorite(ch.id) })
                }
            }
        }

        // Right column: the root's inset occupies the top 162 dp; the details panel sits under it.
        Column(Modifier.width(288.dp).fillMaxHeight()) {
            Spacer(Modifier.height(162.dp + 16.dp))
            val shown = focused?.takeIf { f -> channels.any { it.id == f.id } } ?: channels.firstOrNull()
            val previewFailed = state.preview && state.notice is Notice.NotWorking && state.channelId == shown?.id
            Details(shown, if (shown == null) null else if (previewFailed) "Not working" else statusWord(shown, broken), countries, categories)
        }
    }
}

@Composable
private fun EmptyList(f: ListFilter, countryName: String?) {
    val (title, sub) = when (f.kind) {
        ListFilter.Kind.FAVORITES -> "No favorites yet" to "Hold OK on any channel to add it"
        ListFilter.Kind.RECENT -> "Nothing watched yet" to null
        else -> "No working channels in ${countryName ?: "this list"} right now" to null
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = Tok.md)
        sub?.let { Text(it, style = Tok.sm.copy(color = Tok.muted)) }
    }
}

@Composable
private fun Details(ch: ChannelEntity?, status: String?, countries: Map<String, com.tvapp.data.db.CountryEntity>, categories: Map<String, String>) {
    Column(Modifier.fillMaxSize().background(Tok.panel, Tok.radiusMd).padding(16.dp)) {
        if (ch != null) {
            Logo(ch, 96)
            Spacer(Modifier.height(8.dp))
            Text(ch.name, style = Tok.lg, maxLines = 2, overflow = TextOverflow.Ellipsis)
            ch.region?.let { Text(it, style = Tok.sm.copy(color = Tok.muted), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            ch.country?.let { Text(countries[it]?.name ?: it, style = Tok.sm.copy(color = Tok.muted), maxLines = 1) }
            val cats = ch.categories.split('|').filter { it.isNotEmpty() }.joinToString(" · ") { categories[it] ?: it }
            if (cats.isNotEmpty()) Text(cats, style = Tok.sm.copy(color = Tok.muted), maxLines = 1, overflow = TextOverflow.Ellipsis)
            status?.let { Text(if (it == "no longer available") it else "${statusMark(it)} $it", style = Tok.sm) }
        }
        Spacer(Modifier.weight(1f))
        KeyHints(listOf("OK" to "Watch", "Hold OK" to "Add to favorites", "Hold Back" to "Favorites", "2–9" to "Jump to a letter"))
    }
}

/** Key hints pinned to the bottom of a details panel; every details panel carries the Hold Back line (02-channels.md §1). */
@Composable
fun KeyHints(hints: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        hints.forEach { (key, what) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(key, style = Tok.sm.copy(color = Tok.accent), modifier = Modifier.width(88.dp))
                Text(what, style = Tok.sm.copy(color = Tok.muted), maxLines = 1)
            }
        }
    }
}
