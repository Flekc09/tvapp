package com.tvapp.ui.state

import android.view.KeyEvent

enum class Overlay { NONE, NOT_WORKING, CHANNELS, BROWSE, SEARCH, SETTINGS, ADVANCED, DIAGNOSTICS, CONTEXT_MENU, SOURCES, STRIP, PIN, ADDRESS }

sealed class Action {
    object SurfUp : Action(); object SurfDown : Action(); object ShowBanner : Action(); object ContextMenu : Action()
    object Previous : Action(); object Strip : Action(); object OpenChannels : Action(); object OpenFavorites : Action(); object PlayPause : Action()
    data class Digit(val n: Int) : Action(); object Back : Action(); object None : Action()
}

object KeyRouter {
    fun route(key: Int, longPress: Boolean, doubleTap: Boolean, top: Overlay, bannerVisible: Boolean = false): Action {
        // Keys that work everywhere.
        if (key == KeyEvent.KEYCODE_BACK && longPress) return Action.OpenFavorites // the stick remote has no GUIDE, digits or LAST: hold Back is the favorites key
        when (key) {
            KeyEvent.KEYCODE_BACK -> return if (top == Overlay.NONE && bannerVisible) Action.OpenChannels else Action.Back
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> return Action.PlayPause
            KeyEvent.KEYCODE_LAST_CHANNEL -> return Action.Previous
            KeyEvent.KEYCODE_GUIDE -> return Action.OpenChannels
        }
        if (top == Overlay.STRIP || top == Overlay.CONTEXT_MENU || top == Overlay.SOURCES) when (key) { // player panels: channel keys act on the player (the VM closes the panel first)
            KeyEvent.KEYCODE_CHANNEL_UP -> return Action.SurfUp
            KeyEvent.KEYCODE_CHANNEL_DOWN -> return Action.SurfDown
            KeyEvent.KEYCODE_DPAD_UP -> if (top == Overlay.STRIP) return Action.SurfUp
            KeyEvent.KEYCODE_DPAD_DOWN -> if (top == Overlay.STRIP) return Action.SurfDown
            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> return Action.Digit(key - KeyEvent.KEYCODE_0)
        }
        if (top == Overlay.NOT_WORKING) when (key) { // the card's buttons take OK and Left/Right; everything else acts on the player
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> return if (longPress) Action.ContextMenu else Action.None
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> return Action.None
        }
        if (top != Overlay.NONE && top != Overlay.NOT_WORKING) return Action.None
        return when (key) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> Action.SurfUp
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> Action.SurfDown
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_INFO ->
                if (longPress) Action.ContextMenu else if (doubleTap) Action.Previous else Action.ShowBanner
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> Action.Strip
            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> Action.Digit(key - KeyEvent.KEYCODE_0)
            else -> Action.None
        }
    }
}
