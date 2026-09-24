package com.tvapp.ui

import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import com.tvapp.ui.browse.BrowseOverlay
import com.tvapp.ui.channels.ChannelsOverlay
import com.tvapp.ui.player.Banner
import com.tvapp.ui.player.ChannelStrip
import com.tvapp.ui.player.ContextMenu
import com.tvapp.ui.player.Notices
import com.tvapp.ui.player.NotWorkingCard
import com.tvapp.ui.player.PlayerSurface
import com.tvapp.ui.player.SourcesMenu
import com.tvapp.ui.state.AppViewModel
import com.tvapp.ui.state.Overlay

private val SCRIM_OVERLAYS = setOf(Overlay.NOT_WORKING, Overlay.CHANNELS, Overlay.BROWSE, Overlay.SEARCH, Overlay.SETTINGS, Overlay.ADVANCED,
    Overlay.DIAGNOSTICS, Overlay.CONTEXT_MENU, Overlay.SOURCES, Overlay.PIN, Overlay.ADDRESS)

/**
 * The root: one PlayerSurface, then notices, the banner and the overlay on top of the stack (plan Task 12 step 6).
 * With Channels open the same PlayerView animates to the 288 × 162 inset at the top right of the safe area (design-tokens inset-w).
 */
@Composable
fun App(vm: AppViewModel) {
    val overlays by vm.overlays.collectAsState()
    val top = overlays.lastOrNull() ?: Overlay.NONE
    val state by vm.player.state.collectAsState()
    val bannerVisible by vm.bannerVisible.collectAsState()

    // Keep the screen on only while something plays; cancel() on Home, standby and the sleep timer lets it sleep.
    val window = LocalActivity.current?.window
    LaunchedEffect(state.playing != null) {
        if (window == null) return@LaunchedEffect
        if (state.playing != null) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    val inset by animateFloatAsState(if (Overlay.CHANNELS in overlays) 1f else 0f, tween(250), label = "inset")
    BoxWithConstraints(Modifier.fillMaxSize().background(Tok.bg)) {
        val w = lerp(maxWidth, 288.dp, inset); val h = lerp(maxHeight, 162.dp, inset)
        PlayerSurface(vm.player.player, Modifier.offset(x = lerp(0.dp, maxWidth - 48.dp - 288.dp, inset), y = lerp(0.dp, 27.dp, inset)).size(w, h))

        // The scrim is cut out where the inset is, so the preview stays undimmed, with the 3 dp outline at 60 % white (02-channels.md §1).
        if (top in SCRIM_OVERLAYS) {
            val cut = inset >= 1f
            Box(Modifier.fillMaxSize().graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen).drawBehind {
                drawRect(Tok.scrim)
                if (cut) {
                    val tl = Offset((maxWidth - 48.dp - 288.dp).toPx(), 27.dp.toPx()); val sz = Size(288.dp.toPx(), 162.dp.toPx())
                    drawRoundRect(Color.Black, tl, sz, CornerRadius(16.dp.toPx()), blendMode = BlendMode.Clear)
                    drawRoundRect(Color.White.copy(alpha = 0.6f), tl, sz, CornerRadius(16.dp.toPx()), style = Stroke(3.dp.toPx()))
                }
            })
        }
        val safe = Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 27.dp)
        Box(safe) {
            if (bannerVisible && top == Overlay.NONE) Banner(vm, state, Modifier.align(Alignment.BottomStart))
            when (top) {
                Overlay.NOT_WORKING -> NotWorkingCard(vm, Modifier.align(Alignment.Center))
                Overlay.CONTEXT_MENU -> ContextMenu(vm, Modifier.align(Alignment.BottomStart))
                Overlay.SOURCES -> SourcesMenu(vm, Modifier.align(Alignment.BottomStart))
                Overlay.STRIP -> ChannelStrip(vm, Modifier.align(Alignment.BottomStart))
                Overlay.CHANNELS -> ChannelsOverlay(vm)
                Overlay.BROWSE -> BrowseOverlay(vm)
                Overlay.NONE -> {}
                // Channels, Browse, Search, Settings, Advanced, Diagnostics, PIN and the address dialog arrive in Tasks 13-16.
                else -> Text("${top.name.lowercase().replaceFirstChar { it.uppercase() }} (built in a later task)", style = Tok.md,
                    modifier = Modifier.align(Alignment.TopStart).background(Tok.panel, Tok.radiusMd).padding(16.dp))
            }
        }
        Notices(vm, state, top)
    }
}
