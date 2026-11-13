// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data

import app.aria.domain.HomeLocalSource
import app.aria.domain.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class HomeLocalSourceImpl(private val dao: HomeLocalDao) : HomeLocalSource {
    override fun quickPicks(limit: Int): Flow<List<Track>> = dao.quickPicks(limit).map { r -> r.mapNotNull { it.toTrack() } }
    override fun recentlyPlayed(limit: Int): Flow<List<Track>> = dao.recentlyPlayed(limit).map { r -> r.mapNotNull { it.toTrack() } }
}
