// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.aria.domain.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LibraryRepositoryTest {
    private lateinit var db: AriaDatabase
    private lateinit var repo: LibraryRepositoryImpl
    private var now = 1_000L
    private fun t(n: Int, title: String = "Song $n", artist: String = "Artist $n") =
        Track(TrackId.youtube("track%06d".format(n)), title, listOf(artist), 200_000L, "https://t/$n")

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AriaDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = LibraryRepositoryImpl(db.libraryDao()) { now++ }
    }
    @After fun close() = db.close()

    private suspend fun songs(q: String = "", s: LibrarySort = LibrarySort.RECENTLY_ADDED) = repo.songs(q, s).first()

    @Test fun likeAddsToLibraryAndUnlikeKeepsIt() = runBlocking {
        repo.setLiked(t(1), true)
        assertEquals(listOf(true), songs().map { it.liked })
        assertTrue(repo.isInLibrary(t(1).id).first())
        repo.setLiked(t(1), false)
        assertEquals(listOf(false), songs().map { it.liked })
    }

    @Test fun removingFromLibraryClearsTheLike() = runBlocking {
        repo.setLiked(t(1), true); repo.setInLibrary(t(1), false)
        assertTrue(songs().isEmpty()); assertFalse(repo.isLiked(t(1).id).first())
    }

    @Test fun addToLibraryDoesNotLike() = runBlocking {
        repo.setInLibrary(t(1), true)
        assertEquals(listOf(false), songs().map { it.liked })
    }

    @Test fun playsAloneAreNotInTheLibrary() = runBlocking {
        PlayEventRepositoryImpl(db.playEventDao()).record(t(1), 5_000L, 40_000L)
        assertTrue(songs().isEmpty())
    }

    @Test fun artistsRoundTripInOrder() = runBlocking {
        repo.setInLibrary(Track(TrackId.youtube("track000001"), "Duet", listOf("B", "A"), 1_000L, null), true)
        assertEquals(listOf("B", "A"), songs().single().track.artists)
    }

    @Test fun sortAndFilterRunInSql() = runBlocking {
        repo.setInLibrary(t(1, "Banana", "Zed"), true); repo.setInLibrary(t(2, "apple", "Yan"), true); repo.setInLibrary(t(3, "Cherry", "Xi"), true)
        assertEquals(listOf("Cherry", "apple", "Banana"), songs().map { it.track.title })
        assertEquals(listOf("apple", "Banana", "Cherry"), songs(s = LibrarySort.TITLE).map { it.track.title })
        assertEquals(listOf("apple"), songs("PPL").map { it.track.title })
        assertEquals(listOf("Banana"), songs("zed").map { it.track.title })
    }

    @Test fun filterTreatsPercentAndUnderscoreLiterally() = runBlocking {
        repo.setInLibrary(t(1, "100% Pure"), true); repo.setInLibrary(t(2, "Other"), true)
        assertEquals(listOf("100% Pure"), songs("%").map { it.track.title })
        assertTrue(songs("_").isEmpty())
    }

    @Test fun largeLibraryQueries() = runBlocking {
        for (i in 1..1_500) repo.setInLibrary(t(i), true)
        assertEquals(1_500, songs().size)
        assertEquals("Song 1", songs(s = LibrarySort.TITLE).first().track.title)
    }

    @Test fun albumsNeedInLibraryAndArtistsNeedABookmark() = runBlocking {
        val album = AlbumRef("MPREb_1", "Alb", listOf("Art"), 2020, "https://a")
        repo.setAlbumSaved(album, true)
        assertEquals(listOf(album.copy(thumbnailUrl = "https://a")), repo.albums("", LibrarySort.TITLE).first())
        assertTrue(repo.isAlbumSaved("MPREb_1").first())
        repo.setAlbumSaved(album, false); assertTrue(repo.albums("", LibrarySort.TITLE).first().isEmpty())
        val artist = ArtistRef("UC1", "Singer", null)
        repo.setArtistFollowed(artist, true); assertEquals(listOf(artist), repo.artists("", LibrarySort.TITLE).first())
        repo.setArtistFollowed(artist, false); assertTrue(repo.artists("", LibrarySort.TITLE).first().isEmpty())
    }
}
