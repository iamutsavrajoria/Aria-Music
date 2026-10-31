// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data

import app.aria.data.entities.Event
import app.aria.data.entities.SongEntity
import app.aria.domain.PlayEventRepository
import app.aria.domain.Track
import app.aria.domain.TrackId
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

class PlayEventRepositoryImpl(private val dao: PlayEventDao) : PlayEventRepository {
    override suspend fun record(track: Track, playedAtEpochMs: Long, playTimeMs: Long) {
        val song = SongEntity(
            id = track.id.raw,
            title = track.title,
            duration = track.durationMs?.let { (it / 1000).toInt() } ?: -1,
            thumbnailUrl = track.thumbnailUrl,
        )
        val at = LocalDateTime.ofInstant(Instant.ofEpochMilli(playedAtEpochMs), ZoneOffset.UTC)
        dao.record(song, Event(songId = track.id.raw, timestamp = at, playTime = playTimeMs))
    }

    override suspend fun playCount(id: TrackId): Int = dao.countEvents(id.raw)
}
