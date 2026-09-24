package com.tvapp.ui.player

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tvapp.ui.Tok
import com.tvapp.ui.rememberInteraction
import com.tvapp.ui.state.AppViewModel
import com.tvapp.ui.tvFocus

/**
 * Channel strip #4 (08-dialogs-and-strip.md §7): the current list as cards along the bottom, current channel focused.
 * Focus prefetches but never tunes; OK plays and closes with the banner. Up/Down and Back are routed by KeyRouter / the root Back.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChannelStrip(vm: AppViewModel, modifier: Modifier = Modifier) {
    val channels by vm.visibleChannels.collectAsState()
    val current by vm.bannerChannel.collectAsState()
    val filter by vm.filter.collectAsState()
    val countries by vm.countries.collectAsState()
    val categories by vm.categoryNames.collectAsState()
    val start = channels.indexOfFirst { it.id == current?.id }.coerceAtLeast(0)
    val list = rememberLazyListState(initialFirstVisibleItemIndex = (start - 2).coerceAtLeast(0))
    val focus = remember { FocusRequester() }
    Row(modifier.width(864.dp).height(108.dp).background(Tok.panelStrong, Tok.radiusMd).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(if (channels.isEmpty()) "This list is empty" else listLine(filter, start + 1 to channels.size, countries, categories), style = Tok.sm.copy(color = Tok.muted), maxLines = 3, modifier = Modifier.width(96.dp))
        LazyRow(state = list, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(channels, key = { _, c -> c.id }) { i, ch ->
                val interaction = rememberInteraction()
                val missing = ch.source == AppViewModel.SOURCE_MISSING
                Column(
                    Modifier.width(136.dp).fillMaxHeight().tvFocus(interaction)
                        .then(if (i == start) Modifier.focusRequester(focus) else Modifier)
                        .onFocusChanged { if (it.isFocused) vm.focusInList(ch) }
                        .combinedClickable(interactionSource = interaction, indication = null,
                            onClick = { if (!missing) vm.select(ch.id) },
                            onLongClick = { if (missing) vm.toggleFavorite(ch.id) }) // a placeholder favorite offers Remove
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box { Logo(ch, 48) }
                    Text(if (missing) "no longer available" else ch.name, style = Tok.sm, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
    LaunchedEffect(channels.isNotEmpty()) { if (channels.isNotEmpty()) { list.scrollToItem((start - 2).coerceAtLeast(0)); runCatching { focus.requestFocus() } } }
}

