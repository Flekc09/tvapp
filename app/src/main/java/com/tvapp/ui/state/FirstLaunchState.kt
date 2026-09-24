package com.tvapp.ui.state

import com.tvapp.data.catalog.ImportResult

// Only the interface so far (plan Task 10 step 4); Task 16 fills in the first-launch state machine.
interface Downloader { suspend fun download(onProgress: (read: Long, total: Long, channels: Int) -> Unit): ImportResult }
