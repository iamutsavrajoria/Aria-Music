// SPDX-License-Identifier: GPL-3.0-only
package app.aria.domain.testing

import app.aria.domain.*

object Samples {
    fun track(n: Int, title: String = "Song $n") =
        Track(TrackId.youtube("track%06d".format(n)), title, listOf("Artist $n"), 180_000L, null)
    fun tracks(count: Int, from: Int = 1) = (from until from + count).map { track(it) }
    fun album(n: Int) = AlbumRef("MPREb_$n", "Album $n", listOf("Artist $n"), 2020, null)
    fun artist(n: Int) = ArtistRef("UC$n", "Artist $n", null)
    fun playlist(n: Int) = PlaylistRef("PL$n", "Playlist $n", "Someone", null, 10)
}
