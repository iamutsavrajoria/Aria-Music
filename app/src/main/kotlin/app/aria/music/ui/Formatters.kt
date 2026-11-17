// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui

import java.text.NumberFormat
import java.util.Locale

object Formatters {
    fun duration(ms: Long, locale: Locale = Locale.getDefault()): String {
        val total = (ms / 1000).coerceAtLeast(0); val h = total / 3600; val m = (total % 3600) / 60; val s = total % 60
        return if (h > 0) String.format(locale, "%d:%02d:%02d", h, m, s) else String.format(locale, "%d:%02d", m, s)
    }
    fun count(n: Int, locale: Locale = Locale.getDefault()): String = NumberFormat.getIntegerInstance(locale).format(n)
}
