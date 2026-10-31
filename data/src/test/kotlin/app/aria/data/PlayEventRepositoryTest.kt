// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.aria.domain.Track
import app.aria.domain.TrackId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlayEventRepositoryTest {
    private lateinit var db: AriaDatabase
    private lateinit var repo: PlayEventRepositoryImpl
    private val track = Track(TrackId.youtube("dQw4w9WgXcQ"), "Song", listOf("Artist"), 213_000L, null)

    @Before fun open() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AriaDatabase::class.java).allowMainThreadQueries().build()
        repo = PlayEventRepositoryImpl(db.playEventDao())
    }

    @After fun close() = db.close()

    @Test fun unknownTrackCountsZero() = runBlocking {
        assertEquals(0, repo.playCount(track.id))
    }

    @Test fun recordInsertsSongAndEvents() = runBlocking {
        repo.record(track, playedAtEpochMs = 1_000L, playTimeMs = 30_000L)
        repo.record(track, playedAtEpochMs = 2_000L, playTimeMs = 45_000L)
        assertEquals(2, repo.playCount(track.id))
    }
}
