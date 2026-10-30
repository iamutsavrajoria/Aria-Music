// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.aria.data.entities.TrackMatchEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TrackMatchDaoTest {
    private lateinit var db: AriaDatabase
    private val dao get() = db.trackMatchDao()

    @Before fun open() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AriaDatabase::class.java).allowMainThreadQueries().build()
    }

    @After fun close() = db.close()

    private fun match(target: String, score: Double, manual: Boolean = false) = TrackMatchEntity(
        sourceProvider = "spotify", sourceId = "sp1", targetProvider = "youtube", targetId = target,
        score = score, isManualOverride = manual, cachedAt = 1L,
    )

    @Test fun automaticMatchIsStoredAndFound() = runBlocking {
        dao.upsertAutomatic(match("AAAAAAAAAAA", 0.9))
        assertEquals("AAAAAAAAAAA", dao.find("spotify", "sp1", "youtube")?.targetId)
    }

    @Test fun automaticNeverOverwritesManual() = runBlocking {
        dao.upsertManual(match("MANUAL_ID_1", 1.0, manual = true))
        dao.upsertAutomatic(match("AUTO_ID_222", 0.95))
        val found = dao.find("spotify", "sp1", "youtube")!!
        assertEquals("MANUAL_ID_1", found.targetId)
        assertEquals(true, found.isManualOverride)
    }

    @Test fun manualReplacesAutomatic() = runBlocking {
        dao.upsertAutomatic(match("AUTO_ID_222", 0.95))
        dao.upsertManual(match("MANUAL_ID_1", 1.0, manual = true))
        assertEquals("MANUAL_ID_1", dao.find("spotify", "sp1", "youtube")?.targetId)
    }

    @Test fun reverseLookupFindsSourceByTarget() = runBlocking {
        dao.upsertAutomatic(match("AAAAAAAAAAA", 0.9))
        assertEquals("sp1", dao.findReverse("youtube", "AAAAAAAAAAA", "spotify")?.sourceId)
        assertNull(dao.findReverse("youtube", "ZZZZZZZZZZZ", "spotify"))
    }
}
