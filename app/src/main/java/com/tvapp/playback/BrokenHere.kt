package com.tvapp.playback

import com.tvapp.core.DateProvider
import com.tvapp.data.db.ChannelStatusEntity

class BrokenHere(private val dates: DateProvider) {
    companion object { const val DAYS_REQUIRED = 3; const val STATUS = "broken_here" }
    fun afterFailure(current: ChannelStatusEntity?, channelId: String): ChannelStatusEntity {
        val days = (current?.failedDays?.split('|')?.filter { it.isNotEmpty() } ?: emptyList()).toMutableSet()
        days += dates.today().toString()
        val status = if (days.size >= DAYS_REQUIRED) STATUS else "watch"
        return ChannelStatusEntity(channelId, status, days.sorted().joinToString("|"))
    }
    fun isBroken(s: ChannelStatusEntity?) = s?.status == STATUS
}
