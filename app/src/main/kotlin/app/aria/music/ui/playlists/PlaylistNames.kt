// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.playlists

object PlaylistNames {
    const val MAX = 100

    /** The name as it is stored: trimmed, at most [MAX] characters (code points, so an emoji is never cut in half), or null when nothing is left. */
    fun clean(raw: String): String? = limit(raw.trim()).trim().takeIf { it.isNotEmpty() }

    /** At most [MAX] code points of [raw], untrimmed (for text fields). */
    fun limit(raw: String): String =
        if (raw.codePointCount(0, raw.length) > MAX) raw.substring(0, raw.offsetByCodePoints(0, MAX)) else raw
}
