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
        assertEquals(listOf(album), repo.albums("", LibrarySort.TITLE).first())
        val refreshed = album.copy(thumbnailUrl = "https://b")
        repo.setAlbumSaved(refreshed, true)
        assertEquals(listOf(refreshed), repo.albums("", LibrarySort.TITLE).first())
        assertTrue(repo.isAlbumSaved("MPREb_1").first())
        repo.setAlbumSaved(album, false); assertTrue(repo.albums("", LibrarySort.TITLE).first().isEmpty())
        val artist = ArtistRef("UC1", "Singer", null)
        repo.setArtistFollowed(artist, true); assertEquals(listOf(artist), repo.artists("", LibrarySort.TITLE).first())
        repo.setArtistFollowed(artist, false); assertTrue(repo.artists("", LibrarySort.TITLE).first().isEmpty())
    }

    private fun raw(sql: String): String? = db.openHelper.readableDatabase.query(sql).use { c ->
        assertTrue(c.moveToFirst()); if (c.isNull(0)) null else c.getString(0)
    }
    private fun exec(sql: String) = db.openHelper.writableDatabase.execSQL(sql)

    @Test fun resavingAnExistingAlbumKeepsItsOtherFields() = runBlocking {
        val album = AlbumRef("MPREb_1", "Alb", listOf("Art"), 2020, "https://a")
        repo.setAlbumSaved(album, true)
        val addedAt = raw("SELECT inLibrary FROM album WHERE id='MPREb_1'")
        exec("UPDATE album SET songCount=12, duration=3000, description='desc', themeColor=5, bookmarkedAt=777 WHERE id='MPREb_1'")
        repo.setAlbumSaved(album.copy(year = null, thumbnailUrl = null), true)
        assertEquals("12", raw("SELECT songCount FROM album WHERE id='MPREb_1'"))
        assertEquals("3000", raw("SELECT duration FROM album WHERE id='MPREb_1'"))
        assertEquals("desc", raw("SELECT description FROM album WHERE id='MPREb_1'"))
        assertEquals("5", raw("SELECT themeColor FROM album WHERE id='MPREb_1'"))
        assertEquals("777", raw("SELECT bookmarkedAt FROM album WHERE id='MPREb_1'"))
        assertEquals("2020", raw("SELECT year FROM album WHERE id='MPREb_1'"))
        assertEquals("https://a", raw("SELECT thumbnailUrl FROM album WHERE id='MPREb_1'"))
        assertEquals(addedAt, raw("SELECT inLibrary FROM album WHERE id='MPREb_1'"))
        repo.setAlbumSaved(album, false)
        assertEquals("12", raw("SELECT songCount FROM album WHERE id='MPREb_1'"))
        assertEquals("desc", raw("SELECT description FROM album WHERE id='MPREb_1'"))
        repo.setAlbumSaved(album, true)
        assertEquals("12", raw("SELECT songCount FROM album WHERE id='MPREb_1'"))
        assertTrue(repo.isAlbumSaved("MPREb_1").first())
    }

    @Test fun resavingAnExistingArtistKeepsItsOtherFields() = runBlocking {
        val artist = ArtistRef("UC1", "Singer", "https://old")
        repo.setArtistFollowed(artist, true)
        val followedAt = raw("SELECT bookmarkedAt FROM artist WHERE id='UC1'")
        exec("UPDATE artist SET channelId='chan', isLocal=1, lastUpdateTime=555 WHERE id='UC1'")
        repo.setArtistFollowed(artist.copy(thumbnailUrl = null), true)
        assertEquals("https://old", raw("SELECT thumbnailUrl FROM artist WHERE id='UC1'"))
        assertEquals("chan", raw("SELECT channelId FROM artist WHERE id='UC1'"))
        assertEquals("1", raw("SELECT isLocal FROM artist WHERE id='UC1'"))
        assertEquals("555", raw("SELECT lastUpdateTime FROM artist WHERE id='UC1'"))
        assertEquals(followedAt, raw("SELECT bookmarkedAt FROM artist WHERE id='UC1'"))
        repo.setArtistFollowed(artist, false)
        assertEquals("chan", raw("SELECT channelId FROM artist WHERE id='UC1'"))
        repo.setArtistFollowed(artist.copy(thumbnailUrl = "https://new"), true)
        assertEquals("https://new", raw("SELECT thumbnailUrl FROM artist WHERE id='UC1'"))
        assertEquals("chan", raw("SELECT channelId FROM artist WHERE id='UC1'"))
        assertTrue(repo.isArtistFollowed("UC1").first())
    }

    @Test fun likingTwiceAndAddingTwiceAreIdempotent() = runBlocking {
        repo.setLiked(t(1), true); repo.setLiked(t(1), true)
        assertEquals(listOf(true), songs().map { it.liked })
        assertEquals("1", raw("SELECT COUNT(*) FROM song"))
        assertEquals("1", raw("SELECT COUNT(*) FROM song_artist_map"))
        repo.setInLibrary(t(2), true)
        val first = raw("SELECT inLibrary FROM song WHERE id='${t(2).id.raw}'")
        repo.setInLibrary(t(2), true)
        assertEquals(first, raw("SELECT inLibrary FROM song WHERE id='${t(2).id.raw}'"))
        assertEquals("1", raw("SELECT COUNT(*) FROM song WHERE id='${t(2).id.raw}'"))
        assertEquals(listOf(false), songs("Song 2").map { it.liked })
    }

    @Test fun aSongFirstPlayedThenLikedIsPromotedWithoutLosingFields() = runBlocking {
        val track = t(1)
        PlayEventRepositoryImpl(db.playEventDao()).record(track, 5_000L, 40_000L)
        assertTrue(songs().isEmpty())
        repo.setLiked(track, true)
        val row = songs().single()
        assertEquals(track, row.track); assertTrue(row.liked)
        assertEquals("https://t/1", raw("SELECT thumbnailUrl FROM song WHERE id='${track.id.raw}'"))
        assertEquals("1", raw("SELECT COUNT(*) FROM event WHERE songId='${track.id.raw}'"))
        assertEquals("1", raw("SELECT COUNT(*) FROM song"))
    }

    @Test fun unlikeKeepsTheSongInTheLibrary() = runBlocking {
        repo.setLiked(t(1), true); repo.setLiked(t(1), false)
        assertTrue(repo.isInLibrary(t(1).id).first()); assertFalse(repo.isLiked(t(1).id).first())
        assertNotNull(raw("SELECT inLibrary FROM song WHERE id='${t(1).id.raw}'"))
        assertNull(raw("SELECT likedDate FROM song WHERE id='${t(1).id.raw}'"))
    }

    @Test fun filterTreatsBackslashLiterally() = runBlocking {
        repo.setInLibrary(t(1, "a\\b"), true); repo.setInLibrary(t(2, "ab"), true)
        assertEquals(listOf("a\\b"), songs("\\").map { it.track.title })
        assertEquals(listOf("a\\b"), songs("a\\b").map { it.track.title })
        assertTrue(songs("\\%").isEmpty())
    }
}
