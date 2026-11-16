// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.image

import app.aria.playback.StreamUrlPolicy
import java.net.URI

/** The one place that decides which remote images may load (UI shell decision 6). */
object ImageUrlPolicy {
    private val exactHosts = setOf("i.ytimg.com")
    private val suffixes = listOf(".ytimg.com", ".googleusercontent.com", ".ggpht.com")

    fun isAllowed(url: String): Boolean {
        if (!StreamUrlPolicy.isAllowed(url, allowLocal = false)) return false
        val uri = try { URI(url) } catch (_: Exception) { return false }
        if (uri.userInfo != null || (uri.port != -1 && uri.port != 443)) return false
        val host = uri.host?.lowercase() ?: return false
        return host in exactHosts || suffixes.any { host.endsWith(it) && host.length > it.length }
    }

    /** Lower-case host of [url], or null when it has none. Used for debug logs: never the path, query or user info. */
    fun hostOf(url: String): String? =
        try { URI(url).host?.lowercase()?.takeIf { it.isNotEmpty() } } catch (_: Exception) { null }
}
