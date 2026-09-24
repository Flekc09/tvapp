package com.tvapp.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.tvapp.data.db.ChannelEntity
import com.tvapp.playback.Notice
import com.tvapp.playback.PlayerState
import com.tvapp.ui.Tok
import com.tvapp.ui.state.AppViewModel
import com.tvapp.ui.state.ListFilter
import kotlinx.coroutines.delay
import java.util.Date

/** docs/ui/screens/01-player.md §1: the banner along the bottom of the safe area, 864 × 132 dp. */
@Composable
fun Banner(vm: AppViewModel, state: PlayerState, modifier: Modifier = Modifier) {
    val ch by vm.bannerChannel.collectAsState()
    val position by vm.bannerPosition.collectAsState()
    val filter by vm.filter.collectAsState()
    val favorites by vm.favoriteIds.collectAsState()
    val countries by vm.countries.collectAsState()
    val categories by vm.categoryNames.collectAsState()
    var previous by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state.channelId, ch?.id) { previous = vm.previousName() }
    val channel = ch ?: return

    Row(
        modifier.width(864.dp).height(132.dp).background(Tok.panel, Tok.radiusMd).padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(Modifier.weight(1f)) {
            Logo(channel, 96)
            Spacer(Modifier.width(16.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(channel.name, style = Tok.lg, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 440.dp))
                    if (channel.id in favorites) { Spacer(Modifier.width(8.dp)); Text("★", style = Tok.lg.copy(color = Tok.accent)) }
                }
                Spacer(Modifier.height(8.dp))
                Text(regionLine(channel, countries), style = Tok.sm.copy(color = Tok.muted), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(8.dp))
                Text(statusLine(state, channel), style = Tok.sm, maxLines = 1) // reserved even when empty, so rows never jump
            }
        }
        Spacer(Modifier.width(24.dp))
        Column(horizontalAlignment = Alignment.End) {
            Clock()
            Text(listLine(filter, position, countries, categories), style = Tok.sm.copy(color = Tok.muted), maxLines = 1)
            previous?.let { Text("Previous: $it", style = Tok.sm.copy(color = Tok.muted), maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
fun Logo(channel: ChannelEntity, sizeDp: Int) {
    var loaded by remember(channel.logo) { mutableStateOf(false) }
    Box(Modifier.size(sizeDp.dp).clip(Tok.radiusSm).background(Tok.focusTint), contentAlignment = Alignment.Center) {
        if (!loaded) Text(channel.name.take(1).uppercase(), style = Tok.lg) // the first letter until the logo arrives (or when there is none)
        channel.logo?.let { AsyncImage(model = it, contentDescription = null, modifier = Modifier.size(sizeDp.dp), onSuccess = { loaded = true }) }
    }
}

@Composable
private fun Clock() {
    val context = LocalContext.current
    val format = remember { android.text.format.DateFormat.getTimeFormat(context) } // 12- or 24-hour per the device setting
    var now by remember { mutableStateOf(format.format(Date())) }
    LaunchedEffect(Unit) { while (true) { now = format.format(Date()); delay(60_000 - System.currentTimeMillis() % 60_000) } }
    Text(now, style = Tok.xl2)
}

private fun flag(code: String?): String {
    val c = when (code) { null -> return ""; "UK" -> "GB"; else -> code } // iptv-org files the United Kingdom as UK
    if (c.length != 2 || !c.all { it in 'A'..'Z' }) return ""
    return String(Character.toChars(0x1F1E6 + (c[0] - 'A'))) + String(Character.toChars(0x1F1E6 + (c[1] - 'A')))
}

private fun regionLine(ch: ChannelEntity, countries: Map<String, com.tvapp.data.db.CountryEntity>): String {
    val country = ch.country?.let { countries[it]?.name ?: it }
    val f = countries[ch.country]?.flag?.ifEmpty { null } ?: flag(ch.country)
    return listOfNotNull(f.ifEmpty { null }, listOfNotNull(ch.region, country).joinToString(" · ").ifEmpty { null }).joinToString(" ")
}

// Status words are Working, Not checked, Not working; the measured resolution replaces them once a picture is up (01-player.md §2).
private fun statusLine(s: PlayerState, ch: ChannelEntity): String = when {
    s.channelId != ch.id -> ""
    s.paused -> "Paused"
    s.notice is Notice.NotWorking -> "Not working"
    s.notice is Notice.Trying && (s.notice as Notice.Trying).index > 1 -> "Trying source ${(s.notice as Notice.Trying).index} of ${(s.notice as Notice.Trying).total}"
    s.playing != null -> s.resolution ?: ""
    else -> ""
}

/** "United States · Entertainment · 1,204 of 2,731": list name, middle dot, position. Shared by the banner and the strip. */
internal fun listLine(f: ListFilter, pos: Pair<Int, Int>?, countries: Map<String, com.tvapp.data.db.CountryEntity>, categories: Map<String, String>): String {
    val name = when (f.kind) {
        ListFilter.Kind.FAVORITES -> "Favorites"
        ListFilter.Kind.RECENT -> "Recent"
        ListFilter.Kind.ALL -> "All channels"
        else -> listOfNotNull(f.country?.let { countries[it]?.name ?: it }, f.category?.let { categories[it] ?: it }).joinToString(" · ").ifEmpty { "All channels" }
    }
    return if (pos == null) name else "$name · %,d of %,d".format(pos.first, pos.second)
}
