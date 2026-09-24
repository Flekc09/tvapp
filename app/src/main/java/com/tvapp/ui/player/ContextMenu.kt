package com.tvapp.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tvapp.ui.Tok
import com.tvapp.ui.rememberInteraction
import com.tvapp.ui.state.AppViewModel
import com.tvapp.ui.state.Overlay
import com.tvapp.ui.tvFocus

/** Context menu #2 (docs/ui/screens/06-context-menu-and-sources.md §2): Sources, the favorite toggle, Previous channel. */
@Composable
fun ContextMenu(vm: AppViewModel, modifier: Modifier = Modifier) {
    val state by vm.player.state.collectAsState()
    val favorites by vm.favoriteIds.collectAsState()
    val id = state.channelId
    var previous by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(id) { previous = vm.previousName() }
    val first = remember { FocusRequester() }
    BackHandler { vm.pop(); vm.showBanner() } // Back closes the menu and lands on the banner (§2)
    MenuPanel(vm, height = 300, subtitle = null, modifier = modifier) {
        val playing = state.playing
        MenuItem("Sources", detail = playing?.let { "Playing" }, chevron = true, modifier = Modifier.focusRequester(first)) { vm.push(Overlay.SOURCES) }
        val fav = id != null && id in favorites
        MenuItem(if (fav) "Remove from favorites" else "Add to favorites", lead = if (fav) "★" else "☆") { id?.let { vm.toggleFavorite(it) } } // stays open (§6 decision 3)
        MenuItem("Previous channel", detail = previous ?: "None yet") { if (previous != null) { vm.closeAll(); vm.previous() } }
    }
    LaunchedEffect(Unit) { first.requestFocus() }
}

/** The one bottom-left panel both menus share (§1): header with the channel identity, a hairline, then the items. */
@Composable
fun MenuPanel(vm: AppViewModel, height: Int, subtitle: String?, modifier: Modifier = Modifier, hint: String = "OK Choose  ·  Back Close", items: @Composable () -> Unit) {
    val ch by vm.bannerChannel.collectAsState()
    Column(modifier.width(400.dp).height(height.dp).background(Tok.panel, Tok.radiusMd).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ch?.let { Logo(it, 48); Spacer(Modifier.width(12.dp)) }
            Column {
                Text(ch?.name ?: "", style = Tok.md, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle ?: (ch?.region ?: ch?.country ?: ""), style = Tok.sm.copy(color = Tok.muted), maxLines = 1)
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Color(0x1FFFFFFF))
        Box(Modifier.weight(1f)) { Column { items() } }
        Text(hint, style = Tok.sm.copy(color = Tok.muted))
    }
}

@Composable
fun MenuItem(label: String, modifier: Modifier = Modifier, detail: String? = null, lead: String? = null, chevron: Boolean = false, detailColor: Color = Tok.muted, onClick: () -> Unit) {
    val i = rememberInteraction()
    Row(modifier.fillMaxWidth().height(48.dp).tvFocus(i).clickable(interactionSource = i, indication = null, onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            lead?.let { Text(it, style = Tok.md.copy(color = Tok.accent)); Spacer(Modifier.width(8.dp)) }
            Text(label, style = Tok.md, maxLines = 1)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            detail?.let { Text(it, style = Tok.sm.copy(color = detailColor), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 180.dp)) }
            if (chevron) Text("  ›", style = Tok.md.copy(color = Tok.muted))
        }
    }
}
