// SPDX-License-Identifier: GPL-3.0-only
package app.aria.designsystem

enum class GlassMode { AUTO, ON, OFF }

object GlassResolver {
    private const val API_S = 31

    /** The backdrop blur pipeline needs RenderEffect (Android 12, API 31). */
    fun isSupported(sdkInt: Int): Boolean = sdkInt >= API_S

    fun resolve(mode: GlassMode, sdkInt: Int, batterySaver: Boolean): Boolean {
        if (!isSupported(sdkInt)) return false
        return when (mode) {
            GlassMode.OFF -> false
            GlassMode.ON -> true
            GlassMode.AUTO -> !batterySaver
        }
    }
}
