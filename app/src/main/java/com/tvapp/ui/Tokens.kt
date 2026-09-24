package com.tvapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Values from docs/ui/design-tokens.md. Plain first pass (plan Task 12); the owner restyles the screens afterwards. */
object Tok {
    val bg = Color(0xFF000000)
    val panel = Color(0xD91B2027)        // #1B2027 at 85 %
    val panelStrong = Color(0xF21B2027)  // at 95 %
    val scrim = Color(0x73000000)        // black at 45 %
    val text = Color(0xFFF3F5F8)
    val muted = Color(0xFFC3CAD3)
    val accent = Color(0xFFA8C7FA)
    val focusTint = Color(0x1FFFFFFF)    // white at 12 %
    val focusBorder = Color(0xFFFFFFFF)
    val radiusSm = RoundedCornerShape(8.dp)
    val radiusMd = RoundedCornerShape(16.dp)
    val sm = TextStyle(fontSize = 16.sp, lineHeight = 20.sp, color = text)
    val md = TextStyle(fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium, color = text)
    val lg = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Medium, color = text)
    val xl2 = TextStyle(fontSize = 48.sp, lineHeight = 56.sp, fontFeatureSettings = "tnum", color = text)
}

/** Focus look shared by every focusable row and button: white 12 % fill and a 3 dp white ring. */
@Composable
fun Modifier.tvFocus(interaction: MutableInteractionSource, shape: androidx.compose.ui.graphics.Shape = Tok.radiusSm): Modifier {
    val focused by interaction.collectIsFocusedAsState()
    return if (focused) this.background(Tok.focusTint, shape).border(3.dp, Tok.focusBorder, shape) else this
}

@Composable fun rememberInteraction() = remember { MutableInteractionSource() }

/**
 * Asks for focus once per frame until `landed()` says it arrived (up to ~0.5 s). A LazyColumn row only exists once the
 * list has scrolled to it, so a single request right after scrollToItem can miss (Task 13 emulator check).
 */
suspend fun androidx.compose.ui.focus.FocusRequester.requestWhenReady(landed: () -> Boolean) {
    repeat(30) {
        kotlinx.coroutines.delay(16)
        runCatching { requestFocus() }
        if (landed()) return
    }
}
