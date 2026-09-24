package com.tvapp.ui.player

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.tvapp.data.db.StreamEntity
import com.tvapp.playback.Candidate
import com.tvapp.playback.Notice
import com.tvapp.ui.Tok
import com.tvapp.ui.state.AppViewModel

/**
 * Sources #3 (06-context-menu-and-sources.md §3): the channel's streams in queue order, "Source n · quality · status".
 * OK plays a row at once and the panel stays open, reporting Trying… then Playing in the row (§6 decision 2).
 */
@Composable
fun SourcesMenu(vm: AppViewModel, modifier: Modifier = Modifier) {
    val state by vm.player.state.collectAsState()
    val id = state.channelId ?: return
    var rows by remember { mutableStateOf<List<StreamEntity>>(emptyList()) }
    LaunchedEffect(id) { rows = vm.sourcesFor(id) }
    var picked by remember { mutableStateOf<String?>(null) }
    val subtitle = (state.notice as? Notice.Trying)?.takeIf { picked != null && state.attempting?.url != picked }?.let { "Trying source ${it.index} of ${it.total}" } ?: "Choose a source"
    val focus = remember { FocusRequester() }
    val list = rememberLazyListState()
    MenuPanel(vm, height = 486, subtitle = subtitle, modifier = modifier, hint = "OK Play this source  ·  Back Close") {
        LazyColumn(state = list) {
            itemsIndexed(rows, key = { _, s -> s.url }) { i, s ->
                val playing = state.playing?.url == s.url
                val trying = state.attempting?.url == s.url && state.playing == null
                val word = when {
                    playing -> "Playing"
                    trying -> "Trying…"
                    picked == s.url && state.attempting?.url != s.url -> "Not working"
                    s.health == "up" -> "Working"
                    s.health == "unverified" -> "Not checked"
                    else -> "Not working"
                }
                val isFocusTarget = playing || (state.playing == null && i == 0)
                MenuItem("Source ${i + 1}", detail = listOfNotNull(s.quality, word).joinToString("  ·  "),
                    detailColor = if (playing) Tok.accent else if (trying) Tok.muted else Tok.text,
                    modifier = if (isFocusTarget) Modifier.focusRequester(focus) else Modifier) {
                    picked = s.url
                    vm.player.tune(id, manual = Candidate(s.url, s.health, s.score, s.quality, s.format, s.referrer, s.userAgent))
                }
            }
        }
    }
    LaunchedEffect(rows.isNotEmpty()) {
        if (rows.isEmpty()) return@LaunchedEffect
        val idx = rows.indexOfFirst { it.url == state.playing?.url }.coerceAtLeast(0)
        list.scrollToItem(idx); runCatching { focus.requestFocus() }
    }
}
