// SPDX-License-Identifier: GPL-3.0-only
package app.aria.domain

import kotlinx.coroutines.flow.Flow

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
