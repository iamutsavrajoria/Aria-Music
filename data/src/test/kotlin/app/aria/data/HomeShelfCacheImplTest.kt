// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.aria.data.entities.HomeShelfCacheEntity
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
class HomeShelfCacheImplTest {
    private lateinit var db: AriaDatabase
    private lateinit var cache: HomeShelfCacheImpl
    private fun shelf(t: String) = Shelf(t, null, listOf(BrowseItem.Artist(ArtistRef("UC1", t, null))))

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AriaDatabase::class.java).allowMainThreadQueries().build()
        cache = HomeShelfCacheImpl(db.homeShelfCacheDao())
    }
    @After fun close() = db.close()

    @Test fun emptyIsNull() = runBlocking { assertNull(cache.observe().first()) }

    @Test fun replaceKeepsOrderAndTime() = runBlocking {
        cache.replace(listOf(shelf("A"), shelf("B")), 500L)
        val c = cache.observe().first()!!
        assertEquals(listOf("A", "B"), c.shelves.map { it.title }); assertEquals(500L, c.fetchedAtEpochMs)
        cache.replace(listOf(shelf("C")), 900L)
        assertEquals(listOf("C"), cache.observe().first()!!.shelves.map { it.title })
    }

    @Test fun duplicateShelfTitlesStillGetUniqueSlotKeys() = runBlocking {
        cache.replace(listOf(shelf("Same"), shelf("Same"), shelf("Same")), 500L)
        assertEquals(3, cache.observe().first()!!.shelves.size)
    }

    @Test fun undecodableRowDropsTheWholeCacheSoHomeRefetches() = runBlocking {
        cache.replace(listOf(shelf("A")), 500L)
        db.homeShelfCacheDao().replaceAll(db.homeShelfCacheDao().observe().first() + HomeShelfCacheEntity("yt:09", 9, "garbage", 500L))
        assertNull(cache.observe().first())
        assertTrue(db.homeShelfCacheDao().observe().first().isEmpty())
    }

    @Test fun lateCorruptDropDoesNotWipeANewerReplace() = runBlocking {
        val dao = db.homeShelfCacheDao()
        cache.replace(listOf(shelf("A")), 500L)
        val corrupt = dao.observe().first() + HomeShelfCacheEntity("yt:09", 9, "garbage", 500L)
        dao.replaceAll(corrupt)
        val seenByReader = dao.observe().first()
        cache.replace(listOf(shelf("Fresh")), 900L) // reuses slot key yt:00 while the reader is still deciding
        assertFalse(dao.clearIfUnchanged(seenByReader))
        assertEquals(listOf("Fresh"), cache.observe().first()!!.shelves.map { it.title })
    }

    @Test fun replaceAllRollsBackWhenInsertFails() = runBlocking {
        val dao = db.homeShelfCacheDao()
        cache.replace(listOf(shelf("Keep")), 500L)
        val dup = listOf(HomeShelfCacheEntity("k", 0, "x", 1L), HomeShelfCacheEntity("k", 1, "y", 1L))
        try { dao.replaceAll(dup); fail("expected constraint failure") } catch (e: Exception) { /* expected */ }
        assertEquals(listOf("Keep"), cache.observe().first()!!.shelves.map { it.title })
    }
}
