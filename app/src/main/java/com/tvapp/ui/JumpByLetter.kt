package com.tvapp.ui

import android.view.KeyEvent

/** Digits 2–9 on a CEC remote jump to the next name starting with a letter of that key (02-channels.md §2, 03-browse.md §2). */
object JumpByLetter {
    private val groups = mapOf(2 to "ABC", 3 to "DEF", 4 to "GHI", 5 to "JKL", 6 to "MNO", 7 to "PQRS", 8 to "TUV", 9 to "WXYZ")
    fun digitOf(keyCode: Int): Int? = (keyCode - KeyEvent.KEYCODE_0).takeIf { it in 2..9 }
    /** "5 · JKL", the toast that teaches the mapping. */
    fun label(digit: Int) = "$digit · ${groups.getValue(digit)}"
    /** Index of the next name after `from` (wrapping) whose first letter is on the key, or null. */
    fun next(names: List<String>, from: Int, digit: Int): Int? {
        val letters = groups[digit] ?: return null
        if (names.isEmpty()) return null
        for (step in 1..names.size) {
            val i = (from + step).mod(names.size)
            if (names[i].firstOrNull()?.uppercaseChar()?.let { it in letters } == true) return i
        }
        return null
    }
}
