// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.image

object ImageSizeCap {
    const val MAX_DECODE_PX = 1_024
    /** Unknown or unbounded sides become the cap; known sides are limited to it and at least 1. */
    fun cap(widthPx: Int?, heightPx: Int?): Pair<Int, Int> =
        (widthPx ?: MAX_DECODE_PX).coerceIn(1, MAX_DECODE_PX) to (heightPx ?: MAX_DECODE_PX).coerceIn(1, MAX_DECODE_PX)
}
