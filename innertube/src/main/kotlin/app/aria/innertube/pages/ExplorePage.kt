// SPDX-License-Identifier: GPL-3.0-only
package app.aria.innertube.pages

import app.aria.innertube.models.AlbumItem

data class ExplorePage(
    val newReleaseAlbums: List<AlbumItem>,
    val moodAndGenres: List<MoodAndGenres.Item>,
)
