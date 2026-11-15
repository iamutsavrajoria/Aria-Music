// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data

import app.aria.data.entities.HomeShelfCacheEntity
import app.aria.domain.CachedHome
import app.aria.domain.HomeShelfCache
import app.aria.domain.Shelf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class HomeShelfCacheImpl(private val dao: HomeShelfCacheDao) : HomeShelfCache {
    override fun observe(): Flow<CachedHome?> = dao.observe().map { rows ->
        if (rows.isEmpty()) return@map null
        val decoded = rows.map { it to ShelfJsonCodec.decode(it.payload) }
        if (decoded.any { it.second == null }) {
            // Decision 3: an undecodable row means the cache is from an incompatible build or damaged; drop it all and refetch.
            dao.delete(rows.map { it.shelfKey })
            return@map null
        }
        CachedHome(decoded.map { it.second!! }, rows.minOf { it.fetchedAtEpochMs })
    }

    /** Slot keys are positional ("yt:00", "yt:01", ...), so they are unique per call whatever the shelf titles are. */
    override suspend fun replace(shelves: List<Shelf>, fetchedAtEpochMs: Long) {
        dao.replaceAll(shelves.mapIndexed { i, s -> HomeShelfCacheEntity("yt:%02d".format(i), i, ShelfJsonCodec.encode(s), fetchedAtEpochMs) })
    }
}
