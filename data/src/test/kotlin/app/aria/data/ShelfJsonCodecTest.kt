// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data

import app.aria.domain.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ShelfJsonCodecTest {
    private val shelf = Shelf(
        "Mixed", "Label",
        listOf(
            BrowseItem.Song(Track(TrackId.youtube("dQw4w9WgXcQ"), "S", listOf("A", "B"), 213_000L, "https://i.ytimg.com/x")),
            BrowseItem.Song(Track(TrackId.youtube("kJQP7kiw5Fk"), "NoDur", emptyList(), null, null)),
            BrowseItem.Album(AlbumRef("MPREb_1", "Alb", listOf("Ar"), 2020, "https://a")),
            BrowseItem.Album(AlbumRef("MPREb_2", "Alb2", emptyList(), null, null)),
            BrowseItem.Artist(ArtistRef("UC1", "N", null)),
            BrowseItem.Playlist(PlaylistRef("PL1", "P", "Me", null, 12)),
            BrowseItem.Playlist(PlaylistRef("PL2", "P2", null, "https://p", null)),
        ),
    )

    @Test fun roundTrip() = assertEquals(shelf, ShelfJsonCodec.decode(ShelfJsonCodec.encode(shelf)))
    @Test fun nullSubtitleRoundTrips() = assertEquals(shelf.copy(subtitle = null), ShelfJsonCodec.decode(ShelfJsonCodec.encode(shelf.copy(subtitle = null))))
    @Test fun garbageIsNull() { assertNull(ShelfJsonCodec.decode("not json")); assertNull(ShelfJsonCodec.decode("{}")) }
    @Test fun unknownVersionIsNull() = assertNull(ShelfJsonCodec.decode(ShelfJsonCodec.encode(shelf).replace("\"v\":1", "\"v\":99")))
    @Test fun unknownItemTypeIsNull() = assertNull(ShelfJsonCodec.decode(ShelfJsonCodec.encode(shelf).replace("\"t\":\"artist\"", "\"t\":\"podcast\"")))
    @Test fun invalidSongIdIsNull() = assertNull(ShelfJsonCodec.decode(ShelfJsonCodec.encode(shelf).replace("dQw4w9WgXcQ", "short")))
}
