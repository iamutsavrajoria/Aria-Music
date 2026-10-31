// SPDX-License-Identifier: GPL-3.0-only
package app.aria.innertube.pages

import app.aria.innertube.models.SongItem

data class PlaylistContinuationPage(
    val songs: List<SongItem>,
    val continuation: String?,
)
