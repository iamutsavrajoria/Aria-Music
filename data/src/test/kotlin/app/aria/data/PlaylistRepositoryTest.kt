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
class PlaylistRepositoryTest {
    private lateinit var db: AriaDatabase
    private lateinit var repo: PlaylistRepositoryImpl
    private fun t(n: Int) = Track(TrackId.youtube("track%06d".format(n)), "Song $n", listOf("A"), 1_000L, "https://t/$n")

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AriaDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = PlaylistRepositoryImpl(db.playlistDao())
    }
    @After fun close() = db.close()

    private suspend fun titles(id: String) = repo.tracks(id).first().map { it.track.title }

    @Test fun createRenameListAndCount() = runBlocking {
        val id = repo.create("Mine"); repo.addTracks(id, listOf(t(1), t(2)))
        repo.rename(id, "Renamed")
        val ref = repo.playlists("", LibrarySort.TITLE).first().single()
        assertEquals("Renamed", ref.title); assertEquals(2, ref.trackCount); assertEquals("https://t/1", ref.thumbnailUrl)
        assertEquals("Renamed", repo.playlist(id).first()?.title)
    }

    @Test fun duplicatesAreAllowedAndOrderIsInsertionOrder() = runBlocking {
        val id = repo.create("P"); repo.addTracks(id, listOf(t(1), t(2))); repo.addTracks(id, listOf(t(1)))
        assertEquals(listOf("Song 1", "Song 2", "Song 1"), titles(id))
    }

    @Test fun reorderRewritesPositions() = runBlocking {
        val id = repo.create("P"); repo.addTracks(id, listOf(t(1), t(2), t(3)))
        val ids = repo.tracks(id).first().map { it.entryId }
        repo.reorder(id, listOf(ids[2], ids[0], ids[1]))
        assertEquals(listOf("Song 3", "Song 1", "Song 2"), titles(id))
        repo.addTracks(id, listOf(t(4)))
        assertEquals("Song 4", titles(id).last())
    }

    @Test fun reorderWithWrongIdsIsRejectedAndChangesNothing() = runBlocking {
        val id = repo.create("P"); repo.addTracks(id, listOf(t(1), t(2)))
        val ids = repo.tracks(id).first().map { it.entryId }
        try { repo.reorder(id, listOf(ids[1], 9_999)); fail("expected IllegalArgumentException") } catch (_: IllegalArgumentException) {}
        try { repo.reorder(id, listOf(ids[1])); fail("expected IllegalArgumentException") } catch (_: IllegalArgumentException) {}
        assertEquals(listOf("Song 1", "Song 2"), titles(id))
    }

    @Test fun removeEntryAndDeleteCascade() = runBlocking {
        val id = repo.create("P"); repo.addTracks(id, listOf(t(1), t(2)))
        repo.removeEntry(id, repo.tracks(id).first().first().entryId)
        assertEquals(listOf("Song 2"), titles(id))
        repo.delete(id)
        assertTrue(repo.playlists("", LibrarySort.TITLE).first().isEmpty()); assertTrue(repo.tracks(id).first().isEmpty())
    }

    @Test fun addingTracksDoesNotPutThemInTheLibrary() = runBlocking {
        val id = repo.create("P"); repo.addTracks(id, listOf(t(1)))
        assertTrue(LibraryRepositoryImpl(db.libraryDao()).songs("", LibrarySort.TITLE).first().isEmpty())
    }

    @Test fun blankNamesAreRejected() = runBlocking {
        try { repo.create("   "); fail("expected IllegalArgumentException") } catch (_: IllegalArgumentException) {}
        assertTrue(repo.playlists("", LibrarySort.TITLE).first().isEmpty())
        val id = repo.create("  Mine  ")
        assertEquals("Mine", repo.playlist(id).first()?.title)
        try { repo.rename(id, ""); fail("expected IllegalArgumentException") } catch (_: IllegalArgumentException) {}
        try { repo.rename(id, "  \t "); fail("expected IllegalArgumentException") } catch (_: IllegalArgumentException) {}
        assertEquals("Mine", repo.playlist(id).first()?.title)
    }
}
