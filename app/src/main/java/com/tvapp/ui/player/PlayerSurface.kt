// PlayerView.setKeepContentOnPlayerReset is @UnstableApi (Global Constraints: opt in per file, lint-enforced).
@file:OptIn(UnstableApi::class)

package com.tvapp.ui.player

import android.graphics.Color
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/**
 * The one PlayerView in the app (spec 5.4): the Channels inset is this same view resized, never a second view or decoder.
 * Default SurfaceView surface; the last frame holds across channel changes and a stopped player.
 */
@Composable
fun PlayerSurface(player: ExoPlayer, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                setKeepContentOnPlayerReset(true)
                setShutterBackgroundColor(Color.BLACK)
                isFocusable = false
                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                this.player = player
            }
        },
        modifier = modifier,
    )
}
