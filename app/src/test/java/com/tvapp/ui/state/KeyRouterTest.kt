package com.tvapp.ui.state

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class KeyRouterTest {
    private fun r(key: Int, long: Boolean = false, dbl: Boolean = false, top: Overlay = Overlay.NONE, banner: Boolean = false) = KeyRouter.route(key, long, dbl, top, banner)

    @Test fun playerMapping() {
        assertEquals(Action.SurfUp, r(KeyEvent.KEYCODE_DPAD_UP)); assertEquals(Action.SurfUp, r(KeyEvent.KEYCODE_CHANNEL_UP))
        assertEquals(Action.SurfDown, r(KeyEvent.KEYCODE_DPAD_DOWN)); assertEquals(Action.SurfDown, r(KeyEvent.KEYCODE_CHANNEL_DOWN))
        assertEquals(Action.ShowBanner, r(KeyEvent.KEYCODE_DPAD_CENTER)); assertEquals(Action.ShowBanner, r(KeyEvent.KEYCODE_INFO))
        assertEquals(Action.ContextMenu, r(KeyEvent.KEYCODE_DPAD_CENTER, long = true))
        assertEquals(Action.Previous, r(KeyEvent.KEYCODE_DPAD_CENTER, dbl = true)); assertEquals(Action.Previous, r(KeyEvent.KEYCODE_LAST_CHANNEL))
        assertEquals(Action.Strip, r(KeyEvent.KEYCODE_DPAD_LEFT)); assertEquals(Action.Strip, r(KeyEvent.KEYCODE_DPAD_RIGHT))
        assertEquals(Action.OpenChannels, r(KeyEvent.KEYCODE_GUIDE))
        assertEquals(Action.PlayPause, r(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)); assertEquals(Action.PlayPause, r(KeyEvent.KEYCODE_MEDIA_PLAY))
        assertEquals(Action.Digit(7), r(KeyEvent.KEYCODE_7)); assertEquals(Action.Digit(0), r(KeyEvent.KEYCODE_0))
        assertEquals(Action.Back, r(KeyEvent.KEYCODE_BACK))
        assertEquals(Action.OpenChannels, r(KeyEvent.KEYCODE_BACK, banner = true)) // spec 6: Back from the banner opens Channels
        assertEquals(Action.None, r(KeyEvent.KEYCODE_MENU))
    }
    @Test fun holdBackOpensFavoritesEverywhere() {
        for (t in Overlay.values()) assertEquals("top=$t", Action.OpenFavorites, r(KeyEvent.KEYCODE_BACK, long = true, top = t))
        assertEquals(Action.OpenFavorites, r(KeyEvent.KEYCODE_BACK, long = true, banner = true))
    }
    @Test fun playerPanelsPassChannelKeysThrough() {
        for (t in listOf(Overlay.STRIP, Overlay.CONTEXT_MENU, Overlay.SOURCES)) {
            assertEquals(Action.SurfUp, r(KeyEvent.KEYCODE_CHANNEL_UP, top = t)); assertEquals(Action.Digit(4), r(KeyEvent.KEYCODE_4, top = t))
            assertEquals(Action.None, r(KeyEvent.KEYCODE_DPAD_CENTER, top = t)); assertEquals(Action.Back, r(KeyEvent.KEYCODE_BACK, top = t))
        }
        assertEquals(Action.SurfDown, r(KeyEvent.KEYCODE_DPAD_DOWN, top = Overlay.STRIP)); assertEquals(Action.None, r(KeyEvent.KEYCODE_DPAD_DOWN, top = Overlay.CONTEXT_MENU))
    }
    @Test fun notWorkingCardKeepsPlayerKeysButGivesOkAndLeftRightToItsButtons() {
        assertEquals(Action.None, r(KeyEvent.KEYCODE_DPAD_CENTER, top = Overlay.NOT_WORKING))
        assertEquals(Action.None, r(KeyEvent.KEYCODE_DPAD_LEFT, top = Overlay.NOT_WORKING)); assertEquals(Action.None, r(KeyEvent.KEYCODE_DPAD_RIGHT, top = Overlay.NOT_WORKING))
        assertEquals(Action.ContextMenu, r(KeyEvent.KEYCODE_DPAD_CENTER, long = true, top = Overlay.NOT_WORKING))
        assertEquals(Action.SurfUp, r(KeyEvent.KEYCODE_DPAD_UP, top = Overlay.NOT_WORKING)); assertEquals(Action.SurfDown, r(KeyEvent.KEYCODE_CHANNEL_DOWN, top = Overlay.NOT_WORKING))
        assertEquals(Action.Digit(3), r(KeyEvent.KEYCODE_3, top = Overlay.NOT_WORKING)); assertEquals(Action.Back, r(KeyEvent.KEYCODE_BACK, top = Overlay.NOT_WORKING))
    }
    @Test fun everyPlayerKeyMapsToSomething() {
        for (k in listOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_GUIDE, KeyEvent.KEYCODE_INFO, KeyEvent.KEYCODE_LAST_CHANNEL, KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_5))
            assert(r(k) != Action.None) { "key $k unmapped" }
    }
    @Test fun overlaysOnlyGetBackAndGlobalKeys() {
        assertEquals(Action.None, r(KeyEvent.KEYCODE_DPAD_UP, top = Overlay.CHANNELS)) // Compose focus handles it
        assertEquals(Action.Back, r(KeyEvent.KEYCODE_BACK, top = Overlay.CHANNELS))
        assertEquals(Action.PlayPause, r(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, top = Overlay.CHANNELS))
        assertEquals(Action.Previous, r(KeyEvent.KEYCODE_LAST_CHANNEL, top = Overlay.SETTINGS))
        assertEquals(Action.OpenChannels, r(KeyEvent.KEYCODE_GUIDE, top = Overlay.BROWSE))
        // Key table, owner decision 2026-09-24 (Opus adversarial review 2026-09-23, minor 4): CHANNEL ± is ignored on list overlays, digits go to Compose
        // (jump to a letter in Channels, typed in a text field), LAST is Previous everywhere.
        assertEquals(Action.None, r(KeyEvent.KEYCODE_CHANNEL_UP, top = Overlay.CHANNELS))
        assertEquals(Action.None, r(KeyEvent.KEYCODE_5, top = Overlay.CHANNELS))
        assertEquals(Action.Previous, r(KeyEvent.KEYCODE_LAST_CHANNEL, top = Overlay.PIN))
    }
}
