// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.aria.domain.Track
import app.aria.domain.TrackId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HomeLocalSourceTest {
    private lateinit var db: AriaDatabase
    private lateinit var plays: PlayEventRepositoryImpl
    private lateinit var home: HomeLocalSourceImpl
    private fun t(n: Int) = Track(TrackId.youtube("track%06d".format(n)), "Song $n", listOf("Artist $n"), 1_000L, null)

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AriaDatabase::class.java)
            .allowMainThreadQueries().build()
        plays = PlayEventRepositoryImpl(db.playEventDao()); home = HomeLocalSourceImpl(db.homeLocalDao())
    }
    @After fun close() = db.close()

    @Test fun emptyHistoryGivesEmptySections() = runBlocking {
        assertTrue(home.quickPicks(10).first().isEmpty()); assertTrue(home.recentlyPlayed(10).first().isEmpty())
    }

    @Test fun recentlyPlayedIsNewestFirstWithArtistsAndDistinct() = runBlocking {
        plays.record(t(1), 1_000, 40_000); plays.record(t(2), 2_000, 40_000); plays.record(t(1), 3_000, 40_000)
        val r = home.recentlyPlayed(10).first()
        assertEquals(listOf("Song 1", "Song 2"), r.map { it.title }); assertEquals(listOf("Artist 1"), r.first().artists)
        assertEquals(1, home.recentlyPlayed(1).first().size)
    }

    @Test fun quickPicksRankByPlayCount() = runBlocking {
        plays.record(t(1), 1_000, 1); plays.record(t(2), 2_000, 1); plays.record(t(2), 3_000, 1)
        assertEquals(listOf("Song 2", "Song 1"), home.quickPicks(10).first().map { it.title })
    }

    @Test fun quickPicksSkipSongsHiddenFromQuickPicks() = runBlocking {
        plays.record(t(1), 1_000, 1); plays.record(t(1), 2_000, 1); plays.record(t(2), 3_000, 1)
        db.openHelper.writableDatabase.execSQL("UPDATE song SET hideFromQuickPicks = 1 WHERE id = '${t(1).id.raw}'")
        assertEquals(listOf("Song 2"), home.quickPicks(10).first().map { it.title })
        assertEquals(listOf("Song 2", "Song 1"), home.recentlyPlayed(10).first().map { it.title })
    }
}
