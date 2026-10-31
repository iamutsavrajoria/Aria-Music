// SPDX-License-Identifier: GPL-3.0-only
package app.aria.playback

import app.aria.domain.PlayEventRepository
import app.aria.domain.StreamSource
import app.aria.domain.Track
import app.aria.domain.TrackId

/** Seam over the real Media3 player so the controller is testable on the JVM. */
interface Player {
    fun play(url: String, startPositionMs: Long)
    fun positionMs(): Long
    fun stop()
}

interface ResumeStore {
    fun save(id: TrackId, positionMs: Long)
    fun load(id: TrackId): Long
}

class PlayerController(
    private val streams: StreamSource,
    private val events: PlayEventRepository,
    private val player: Player,
    private val resume: ResumeStore,
    private val clock: () -> Long,
) {
    private var startedAt: Long = 0L

    suspend fun start(track: Track): Result<Unit> =
        streams.resolve(track.id).map { stream ->
            player.play(stream.url, resume.load(track.id))
            startedAt = clock()
        }

    suspend fun stopAndRecord(track: Track) {
        val position = player.positionMs()
        val playTime = clock() - startedAt
        player.stop()
        resume.save(track.id, position)
        events.record(track, playedAtEpochMs = startedAt, playTimeMs = playTime)
    }
}
