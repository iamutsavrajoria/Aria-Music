// SPDX-License-Identifier: GPL-3.0-only
package app.aria.domain

import app.aria.domain.testing.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class DefaultHomeRepositoryTest {
    private val browse = FakeBrowseSource(); private val cache = FakeHomeShelfCache(); private var now = 10_000_000L
    private val repo = DefaultHomeRepository(FakeHomeLocalSource(), browse, cache) { now }
    private fun shelf(t: String) = Shelf(t, null, listOf(BrowseItem.Song(Samples.track(1))))

    @Test fun refreshStoresReadyShelvesAndReportsFailedOnes() = runBlocking {
        browse.homeResult = Result.success(listOf(ShelfState.Ready(shelf("A")), ShelfState.Failed("B")))
        val r = repo.refresh().getOrThrow()
        assertEquals(2, r.shelves.size)
        assertEquals(listOf("A"), cache.state.value!!.shelves.map { it.title }); assertEquals(now, cache.state.value!!.fetchedAtEpochMs)
    }

    @Test fun failedFetchKeepsTheCache() = runBlocking {
        cache.state.value = CachedHome(listOf(shelf("Old")), 1L)
        browse.homeResult = Result.failure(java.io.IOException("offline"))
        assertTrue(repo.refresh().isFailure)
        assertEquals(listOf("Old"), cache.state.value!!.shelves.map { it.title }); assertEquals(0, cache.replaceCalls)
    }

    @Test fun nothingUsableKeepsTheCache() = runBlocking {
        cache.state.value = CachedHome(listOf(shelf("Old")), 1L)
        browse.homeResult = Result.success(listOf(ShelfState.Failed("X")))
        assertTrue(repo.refresh().isFailure); assertEquals(0, cache.replaceCalls)
    }

    @Test fun remoteIsNullWithoutCacheAndOutdatedAfterSevenDays() = runBlocking {
        assertNull(repo.remote().first())
        cache.state.value = CachedHome(listOf(shelf("A")), now - 6L * 24 * 3600_000)
        assertFalse(repo.remote().first()!!.outdated)
        cache.state.value = CachedHome(listOf(shelf("A")), now - 8L * 24 * 3600_000)
        assertTrue(repo.remote().first()!!.outdated)
    }

    @Test fun revalidationAgeIsFifteenMinutes() = runBlocking {
        assertTrue(repo.needsRevalidation())
        cache.state.value = CachedHome(listOf(shelf("A")), now - 14 * 60_000L); assertFalse(repo.needsRevalidation())
        cache.state.value = CachedHome(listOf(shelf("A")), now - 16 * 60_000L); assertTrue(repo.needsRevalidation())
    }

    @Test fun exactBoundariesAreStillFresh() = runBlocking {
        cache.state.value = CachedHome(listOf(shelf("A")), now - DefaultHomeRepository.REVALIDATE_AFTER_MS); assertFalse(repo.needsRevalidation())
        cache.state.value = CachedHome(listOf(shelf("A")), now - DefaultHomeRepository.REVALIDATE_AFTER_MS - 1); assertTrue(repo.needsRevalidation())
        cache.state.value = CachedHome(listOf(shelf("A")), now - DefaultHomeRepository.OUTDATED_AFTER_MS); assertFalse(repo.remote().first()!!.outdated)
        cache.state.value = CachedHome(listOf(shelf("A")), now - DefaultHomeRepository.OUTDATED_AFTER_MS - 1); assertTrue(repo.remote().first()!!.outdated)
    }

    @Test fun cacheFromTheFutureIsStale() = runBlocking {
        cache.state.value = CachedHome(listOf(shelf("A")), now + 60_000L)
        assertTrue(repo.needsRevalidation())
    }

    @Test fun remoteReEmitsAfterRefresh() = runBlocking {
        cache.state.value = CachedHome(listOf(shelf("Old")), 1L)
        val seen = mutableListOf<String?>()
        val job = launch(kotlinx.coroutines.Dispatchers.Unconfined) { repo.remote().collect { seen += it?.shelves?.first()?.title } }
        browse.homeResult = Result.success(listOf(ShelfState.Ready(shelf("New"))))
        repo.refresh().getOrThrow()
        job.cancel()
        assertEquals(listOf<String?>("Old", "New"), seen)
    }
}
