package com.tvapp.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.dp
import com.tvapp.playback.Notice
import com.tvapp.playback.PlayerState
import com.tvapp.ui.Tok
import com.tvapp.ui.state.AppViewModel
import com.tvapp.ui.state.Overlay
import kotlinx.coroutines.delay

/**
 * The one-line toast (top centre) and the poor-signal prompt (top right), inside the safe area (01-player.md §1).
 * "Trying source i of n" lives in the banner's status row; "This channel isn't working" is the NOT_WORKING overlay, not a notice.
 * The player's own source notices are suppressed while an overlay is open; toasts are not.
 */
@Composable
fun Notices(vm: AppViewModel, state: PlayerState, top: Overlay) {
    val toast by vm.toast.collectAsState()
    val exitArmed by vm.exitArmed.collectAsState()
    // "Switching source" shows for banner-hold (3 s) from the moment the notice appears.
    var switching by remember { mutableStateOf(false) }
    LaunchedEffect(state.notice) { if (state.notice == Notice.Switching) { switching = true; delay(3000) }; switching = false }
    val playerLine = if (top != Overlay.NONE) null else when {
        state.notice == Notice.BackToLive -> "Back to live"
        switching -> "Switching source"
        else -> null
    }
    val line = toast ?: (if (exitArmed) "Press Back again to exit" else null) ?: playerLine
    Box(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 27.dp)) {
        line?.let {
            Text(it, style = Tok.sm, modifier = Modifier.align(Alignment.TopCenter).background(Tok.panel, Tok.radiusSm).padding(horizontal = 24.dp, vertical = 10.dp))
        }
        if (state.notice == Notice.PoorSignal && top == Overlay.NONE) {
            Column(Modifier.align(Alignment.TopEnd).width(320.dp).background(Tok.panel, Tok.radiusMd).padding(16.dp)) {
                Text("Picture is struggling", style = Tok.md)
                Text("Press OK to try another source", style = Tok.sm.copy(color = Tok.muted))
            }
        }
    }
}
