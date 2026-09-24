package com.tvapp.ui.channels

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tvapp.data.db.ChannelEntity
import com.tvapp.ui.Tok
import com.tvapp.ui.player.Logo
import com.tvapp.ui.rememberInteraction
import com.tvapp.ui.state.AppViewModel
import com.tvapp.ui.tvFocus

/** Status words (CLAUDE.md): Working, Not checked, Not working. Broken here reads Not working. */
fun statusWord(ch: ChannelEntity, broken: Set<String>): String = when {
    ch.source == AppViewModel.SOURCE_MISSING -> "no longer available"
    ch.id in broken -> "Not working"
    ch.bestHealth == "up" -> "Working"
    ch.bestHealth == "unverified" -> "Not checked"
    else -> "Not working"
}
fun statusMark(word: String) = when (word) { "Working" -> "●"; "Not checked" -> "○"; else -> "✕" }

/**
 * One list row (02-channels.md §1, row anatomy): [favorite number] · logo-sm · name / region + a fixed 112 dp status slot.
 * The status slot never shrinks; only the region ellipsises. No flag: the column already names the country.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChannelRow(ch: ChannelEntity, number: Int?, broken: Set<String>, modifier: Modifier = Modifier, onClick: () -> Unit, onLongClick: () -> Unit) {
    val i = rememberInteraction()
    val word = statusWord(ch, broken)
    Row(
        modifier.fillMaxWidth().height(64.dp).tvFocus(i)
            .combinedClickable(interactionSource = i, indication = null, onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        number?.let { Text("$it", style = Tok.md, textAlign = TextAlign.End, modifier = Modifier.width(28.dp)); Spacer(Modifier.width(4.dp)) }
        Logo(ch, 48)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(ch.name, style = Tok.md, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(ch.region ?: "", style = Tok.sm.copy(color = Tok.muted), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (ch.source == AppViewModel.SOURCE_MISSING) Text(word, style = Tok.sm.copy(color = Tok.muted), maxLines = 1)
                else Text("${statusMark(word)} $word", style = Tok.sm, maxLines = 1, softWrap = false, modifier = Modifier.width(112.dp), textAlign = TextAlign.End)
            }
        }
    }
}
