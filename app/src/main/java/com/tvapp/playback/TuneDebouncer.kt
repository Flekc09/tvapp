package com.tvapp.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class TuneDebouncer(private val scope: CoroutineScope, private val delayMs: Long = 300, private val onTune: suspend (String) -> Unit) {
    private var job: Job? = null
    var pending: String? = null; private set
    fun request(channelId: String) {
        pending = channelId
        job?.cancel()
        job = scope.launch { delay(delayMs); val id = pending; pending = null; if (id != null) onTune(id) }
    }
}
