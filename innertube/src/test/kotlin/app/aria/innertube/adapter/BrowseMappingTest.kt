// SPDX-License-Identifier: GPL-3.0-only
package app.aria.innertube.adapter

import app.aria.domain.BrowseItem
import app.aria.domain.ShelfState
import app.aria.innertube.models.*
import app.aria.innertube.pages.*
import org.junit.Assert.*
import org.junit.Test

class BrowseMappingTest {
    private fun song(id: String, title: String = id) = SongItem(id = id, title = title, artists = listOf(Artist("A", "UC1")), thumbnail = "https://i.ytimg.com/x.jpg")
    private fun album(n: Int) = AlbumItem(browseId = "MPREb_$n", playlistId = "OLAK$n", title = "Alb $n", artists = listOf(Artist("Ar", null)), year = 2021, thumbnail = "https://lh3.googleusercontent.com/a")
    private fun artist(n: Int) = ArtistItem(id = "UC$n", title = "Art $n", thumbnail = null, shuffleEndpoint = null, radioEndpoint = null)
    private fun playlist(id: String, count: String?, podcast: Boolean = false) = PlaylistItem(
        id = id, title = "PL", author = Artist("Me", null), songCountText = count, thumbnail = null,
        playEndpoint = null, shuffleEndpoint = null, radioEndpoint = null, isPodcast = podcast,
    )

    @Test fun invalidVideoIdIsDroppedNotThrown() {
        assertNull(song("short").toTrackOrNull())
        assertEquals("dQw4w9WgXcQ", song("dQw4w9WgXcQ").toTrackOrNull()?.id?.raw)
    }

    @Test fun podcastsAndEpisodesAreNotMapped() {
        assertNull(playlist("PLx", "3 songs", podcast = true).toBrowseItem())
        assertNull(song("dQw4w9WgXcQ").copy(isEpisode = true).toBrowseItem())
        assertNull(PodcastItem("PC", "P", null, null, null, null, null).toBrowseItem())
    }

    @Test fun playlistCountIsParsedFromDisplayText() {
        assertEquals(12, playlist("PL1", "12 songs").toRef().trackCount)
        assertEquals(1234, playlist("PL1", "1,234 songs").toRef().trackCount)
        assertNull(playlist("PL1", null).toRef().trackCount)
        assertNull(playlist("PL1", "many").toRef().trackCount)
    }

    @Test fun homeShelvesFailIndependently() {
        val page = HomePage(
            chips = null, continuation = null,
            sections = listOf(
                HomePage.Section("Good", "Label", null, null, listOf(song("dQw4w9WgXcQ"), album(1), artist(1), song("bad"))),
                HomePage.Section("Bad", null, null, null, listOf(album(2), album(3))),
                HomePage.Section("Podcasts only", null, null, null, listOf(PodcastItem("PC", "P", null, null, null, null, null))),
                HomePage.Section("Empty", null, null, null, emptyList()),
            ),
        )
        val states = page.toShelfStates { item -> if ((item as? AlbumItem)?.browseId in setOf("MPREb_2", "MPREb_3")) error("boom") else item.toBrowseItem() }
        assertEquals(2, states.size)
        val good = (states[0] as ShelfState.Ready).shelf
        assertEquals("Good", good.title); assertEquals("Label", good.subtitle)
        assertEquals(listOf("song", "album", "artist"), good.items.map { it.key.substringBefore(':') })
        assertEquals(ShelfState.Failed("Bad"), states[1])
    }

    @Test fun duplicateItemsInAShelfAreCollapsedByKey() {
        val page = HomePage(
            chips = null,
            sections = listOf(HomePage.Section("Dupes", null, null, null, listOf(song("dQw4w9WgXcQ"), album(1), song("dQw4w9WgXcQ"), album(1), artist(1)))),
        )
        val shelf = (page.toShelfStates().single() as ShelfState.Ready).shelf
        assertEquals(listOf("song:dQw4w9WgXcQ", "album:MPREb_1", "artist:UC1"), shelf.items.map { it.key })
        val d = ArtistPage(artist(1), listOf(ArtistSection("Albums", listOf(album(1), album(1)), null)), null, null).toDetail()
        assertEquals(1, d.shelves.single().items.size)
    }

    @Test fun albumPageMapsAndDropsBadTracks() {
        val d = AlbumPage(album(1), listOf(song("dQw4w9WgXcQ"), song("nope")), listOf(album(2))).toDetail()
        assertEquals("MPREb_1", d.album.id); assertEquals(listOf("Ar"), d.album.artists); assertEquals(1, d.tracks.size)
        assertEquals(listOf("MPREb_2"), d.otherVersions.map { it.id })
    }

    @Test fun artistPageDropsEmptySectionsAndKeepsOrder() {
        val d = ArtistPage(
            artist(1), listOf(ArtistSection("Songs", listOf(song("dQw4w9WgXcQ")), null), ArtistSection("Nothing", emptyList(), null), ArtistSection("Albums", listOf(album(1)), null)),
            "desc", "1M subscribers",
        ).toDetail()
        assertEquals(listOf("Songs", "Albums"), d.shelves.map { it.title }); assertEquals("desc", d.description)
        assertEquals(1, d.topTracks.size)
    }

    @Test fun playlistPageUsesTheSongsContinuation() {
        val d = PlaylistPage(playlist("PL1", "2 songs"), listOf(song("dQw4w9WgXcQ")), songsContinuation = "tok", continuation = "other").toDetail()
        assertEquals("tok", d.continuation); assertEquals("Me", d.playlist.subtitle)
        assertEquals("other", PlaylistPage(playlist("PL1", null), emptyList(), null, "other").toDetail().continuation)
    }

    @Test fun searchGroupsByTypeNotByTitle() {
        val r = listOf(song("dQw4w9WgXcQ"), song("dQw4w9WgXcQ"), album(1), artist(1), playlist("PL1", null), song("bad")).toSearchResults()
        assertEquals(1, r.songs.size); assertEquals(1, r.albums.size); assertEquals(1, r.artists.size); assertEquals(1, r.playlists.size)
    }

    @Test fun emptyPagesMapToEmptyLists() {
        assertTrue(HomePage(null, emptyList()).toShelfStates().isEmpty())
        assertTrue(emptyList<YTItem>().toSearchResults().isEmpty)
        assertTrue(AlbumPage(album(1), emptyList(), emptyList()).toDetail().tracks.isEmpty())
    }
}
