package com.tvapp.playback

import com.tvapp.core.Clock

// Stub until Task 15, which replaces this file with the real rule (plan Task 9 step 6): never prompts.
class PoorSignal(private val clock: Clock, private val declaredQuality: () -> String?) {
    fun reset() {}
    fun onRebuffer() {}
    fun onResolution(height: Int) {}
    fun shouldPrompt(channelId: String): Boolean = false
}
