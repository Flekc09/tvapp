package com.tvapp.ui.state

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PreviewController(private val scope: CoroutineScope, private val dwellMs: Long = 700, var currentChannelId: String?, private val onSwitch: suspend (String) -> Unit) {
    private var job: Job? = null
    fun focus(channelId: String?) {
        job?.cancel()
        if (channelId == null || channelId == currentChannelId) return
        job = scope.launch { delay(dwellMs); currentChannelId = channelId; onSwitch(channelId) }
    }
    fun stop() { job?.cancel(); job = null }
}
