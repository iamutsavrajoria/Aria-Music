// SPDX-License-Identifier: GPL-3.0-only
package app.aria.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Local Home sections; they work fully offline. */
interface HomeLocalSource {
    fun quickPicks(limit: Int): Flow<List<Track>>
    fun recentlyPlayed(limit: Int): Flow<List<Track>>
}

data class CachedHome(val shelves: List<Shelf>, val fetchedAtEpochMs: Long)

/** Parsed Home shelves kept between runs (decision 3). Undecodable rows are dropped by the implementation. */
interface HomeShelfCache {
    fun observe(): Flow<CachedHome?>
    suspend fun replace(shelves: List<Shelf>, fetchedAtEpochMs: Long)
}

data class HomeRemote(val shelves: List<Shelf>, val fetchedAtEpochMs: Long, val outdated: Boolean)

/** Result of a successful refresh: every shelf of the fresh response, failed ones included. */
data class HomeRefresh(val shelves: List<ShelfState>)

interface HomeRepository {
    fun quickPicks(limit: Int): Flow<List<Track>>
    fun recentlyPlayed(limit: Int): Flow<List<Track>>
    /** Cached remote shelves; emits null when there is no usable cache. Re-emits after [refresh]. */
    fun remote(): Flow<HomeRemote?>
    /** True when the cache is missing or older than the revalidation age. */
    suspend fun needsRevalidation(): Boolean
    /** Fetches fresh shelves and replaces the cache. A failure leaves the cache untouched. */
    suspend fun refresh(): Result<HomeRefresh>
}

class DefaultHomeRepository(
    private val local: HomeLocalSource,
    private val browse: BrowseSource,
    private val cache: HomeShelfCache,
    private val clock: () -> Long = System::currentTimeMillis,
) : HomeRepository {
    override fun quickPicks(limit: Int) = local.quickPicks(limit)
    override fun recentlyPlayed(limit: Int) = local.recentlyPlayed(limit)

    override fun remote(): Flow<HomeRemote?> = cache.observe().map { c ->
        c?.takeIf { it.shelves.isNotEmpty() }?.let {
            HomeRemote(it.shelves, it.fetchedAtEpochMs, outdated = clock() - it.fetchedAtEpochMs > OUTDATED_AFTER_MS)
        }
    }

    override suspend fun needsRevalidation(): Boolean {
        val c = cache.observe().first() ?: return true
        val age = clock() - c.fetchedAtEpochMs
        // A negative age (fetched "in the future", e.g. the clock moved back) cannot be trusted: treat it as stale.
        // Exactly REVALIDATE_AFTER_MS old is still fresh (exclusive boundary).
        return age < 0 || age > REVALIDATE_AFTER_MS
    }

    override suspend fun refresh(): Result<HomeRefresh> {
        val states = browse.home().getOrElse { return Result.failure(it) }
        val ready = states.filterIsInstance<ShelfState.Ready>().map { it.shelf }
        if (ready.isEmpty()) return Result.failure(IllegalStateException("Home returned no usable shelves"))
        cache.replace(ready, clock())
        return Result.success(HomeRefresh(states))
    }

    companion object {
        const val REVALIDATE_AFTER_MS = 15 * 60_000L
        const val OUTDATED_AFTER_MS = 7L * 24 * 3600_000
    }
}
