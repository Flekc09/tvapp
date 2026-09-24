package com.tvapp.ui.state

import java.util.Locale

/** The device's country as an iptv-org country code. */
fun deviceCountry(locale: Locale = Locale.getDefault()): String = when (val c = locale.country) {
    "" -> "US"
    "GB" -> "UK"
    else -> c
}
