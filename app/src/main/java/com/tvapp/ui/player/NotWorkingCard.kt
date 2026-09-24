package com.tvapp.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tvapp.ui.Tok
import com.tvapp.ui.rememberInteraction
import com.tvapp.ui.state.AppViewModel
import com.tvapp.ui.tvFocus

/** Card #13 (01-player.md §2 Error): an overlay, not a notice, so its two buttons take focus. Next channel is focused first. */
@Composable
fun NotWorkingCard(vm: AppViewModel, modifier: Modifier = Modifier) {
    val ch by vm.bannerChannel.collectAsState()
    val next = remember { FocusRequester() }
    Row(modifier.width(480.dp).height(200.dp).background(Tok.panelStrong, Tok.radiusMd).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        ch?.let { Logo(it, 96); Spacer(Modifier.width(16.dp)) }
        Column {
            Text("This channel isn't working right now", style = Tok.lg)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CardButton("Next channel", primary = true, modifier = Modifier.focusRequester(next)) { vm.surf(+1) }
                CardButton("Try again", primary = false) { vm.player.retuneToLive() }
            }
        }
    }
    LaunchedEffect(Unit) { next.requestFocus() }
}

@Composable
fun CardButton(label: String, primary: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val i = rememberInteraction()
    Text(label, style = Tok.md.copy(color = if (primary) Color(0xFF0B1B33) else Tok.text),
        modifier = modifier.height(48.dp).background(if (primary) Tok.accent else Color(0x1FFFFFFF), Tok.radiusSm).tvFocus(i)
            .clickable(interactionSource = i, indication = null, onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp))
}
