// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
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
class SearchHistoryRepositoryTest {
    private lateinit var db: AriaDatabase
    private lateinit var repo: SearchHistoryRepositoryImpl

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AriaDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = SearchHistoryRepositoryImpl(db.searchHistoryDao())
    }
    @After fun close() = db.close()

    @Test fun newestFirstAndReAddMovesToTop() = runBlocking {
        repo.add("a"); repo.add("b"); repo.add("a")
        assertEquals(listOf("a", "b"), repo.recent(10).first())
    }
    @Test fun removeAndClear() = runBlocking {
        repo.add("a"); repo.add("b"); repo.remove("a")
        assertEquals(listOf("b"), repo.recent(10).first())
        repo.clear(); assertTrue(repo.recent(10).first().isEmpty())
    }
    @Test fun capsAtFifty() = runBlocking {
        for (i in 1..60) repo.add("q$i")
        val r = repo.recent(100).first()
        assertEquals(50, r.size); assertEquals("q60", r.first()); assertEquals("q11", r.last())
    }
    @Test fun blankQueriesAreIgnored() = runBlocking { repo.add("   "); assertTrue(repo.recent(10).first().isEmpty()) }
}
