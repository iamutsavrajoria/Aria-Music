// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.playlists

object PlaylistNames {
    const val MAX = 100

    /** The name as it is stored: trimmed, at most [MAX] characters, or null when nothing is left. */
    fun clean(raw: String): String? = raw.trim().take(MAX).trim().takeIf { it.isNotEmpty() }
}
