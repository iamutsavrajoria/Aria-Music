// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.aria.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class QueueStoreTest {
    private lateinit var db: AriaDatabase
    private lateinit var store: QueueStoreImpl
    private fun item(id: Long, vid: String) =
        QueueItem(id, Track(TrackId.youtube(vid), "Title $id", listOf("A", "B"), 123_000L, "https://t/$id"))

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AriaDatabase::class.java)
            .allowMainThreadQueries().build()
        store = QueueStoreImpl(db.queueDao())
    }
    @After fun close() = db.close()

    @Test fun loadIsNullWhenNothingSaved() = runBlocking { assertNull(store.load()) }

    @Test fun roundTripKeepsEverything() = runBlocking {
        val items = listOf(item(3, "aaaaaaaaaa1"), item(1, "aaaaaaaaaa2"), item(2, "aaaaaaaaaa3"))
        val q = SavedQueue(items, listOf(1, 2, 3), shuffle = true, repeat = RepeatMode.ALL, nextQueueId = 4)
        store.saveStructure(q); store.savePosition(SavedPosition(1, 42_000))
        val r = store.load()!!
        assertEquals(q, r.queue); assertEquals(SavedPosition(1, 42_000), r.position)
    }

    @Test fun structureReplacesOldRows() = runBlocking {
        store.saveStructure(SavedQueue(listOf(item(1, "aaaaaaaaaa1"), item(2, "aaaaaaaaaa2")), listOf(1, 2), false, RepeatMode.OFF, 3))
        store.saveStructure(SavedQueue(listOf(item(2, "aaaaaaaaaa2")), listOf(2), false, RepeatMode.ONE, 3))
        store.savePosition(SavedPosition(0, 0))
        val r = store.load()!!
        assertEquals(listOf(2L), r.queue.items.map { it.queueId }); assertEquals(RepeatMode.ONE, r.queue.repeat)
    }

    @Test fun positionSaveDoesNotTouchStructure() = runBlocking {
        val q = SavedQueue(listOf(item(1, "aaaaaaaaaa1")), listOf(1), false, RepeatMode.OFF, 2)
        store.saveStructure(q); store.savePosition(SavedPosition(0, 1)); store.savePosition(SavedPosition(0, 2))
        assertEquals(q, store.load()!!.queue); assertEquals(2L, store.load()!!.position.positionMs)
    }
}
