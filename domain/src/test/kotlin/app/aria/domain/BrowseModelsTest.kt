// SPDX-License-Identifier: GPL-3.0-only
package app.aria.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BrowseModelsTest {
    private fun track(n: Int) = Track(TrackId.youtube("track%06d".format(n)), "T$n", listOf("A"), 1_000L, null)

    @Test fun keysAreUniquePerKindAndId() {
        val keys = listOf(
            BrowseItem.Song(track(1)).key,
            BrowseItem.Album(AlbumRef("track000001", "T", emptyList(), null, null)).key,
            BrowseItem.Artist(ArtistRef("track000001", "T", null)).key,
            BrowseItem.Playlist(PlaylistRef("track000001", "T", null, null, null)).key,
        )
        assertEquals(4, keys.toSet().size)
    }

    @Test fun topTracksComeFromTheFirstShelfWithSongs() {
        val albums = Shelf("Albums", null, listOf(BrowseItem.Album(AlbumRef("a", "A", emptyList(), null, null))))
        val songs = Shelf("Songs", null, listOf(BrowseItem.Song(track(1)), BrowseItem.Song(track(2))))
        val d = ArtistDetail(ArtistRef("UC1", "N", null), null, null, listOf(albums, songs))
        assertEquals(listOf(track(1), track(2)), d.topTracks)
        assertTrue(ArtistDetail(ArtistRef("UC1", "N", null), null, null, listOf(albums)).topTracks.isEmpty())
    }

    @Test fun settingsAreClamped() {
        assertEquals(10_000L, AppSettings(historyThresholdMs = 1).normalized().historyThresholdMs)
        assertEquals(120_000L, AppSettings(historyThresholdMs = 9_999_999).normalized().historyThresholdMs)
        assertEquals(45_000L, AppSettings(historyThresholdMs = 45_000).normalized().historyThresholdMs)
        assertEquals(1, AppSettings(sleepTimerDefaultMinutes = 0).normalized().sleepTimerDefaultMinutes)
        assertEquals(240, AppSettings(sleepTimerDefaultMinutes = 9_999).normalized().sleepTimerDefaultMinutes)
    }

    @Test fun emptySearchResults() {
        assertTrue(SearchResults(emptyList(), emptyList(), emptyList(), emptyList()).isEmpty)
        assertFalse(SearchResults(listOf(track(1)), emptyList(), emptyList(), emptyList()).isEmpty)
    }
}
