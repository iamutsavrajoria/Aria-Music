// SPDX-License-Identifier: GPL-3.0-only
package app.aria.domain

data class Track(
    val id: TrackId,
    val title: String,
    val artists: List<String>,
    val durationMs: Long?,
    val thumbnailUrl: String?,
)

data class ResolvedStream(
    val url: String,
    val mimeType: String,
    val bitrate: Int,
    val contentLength: Long?,
    val expiresAtEpochMs: Long?,
    /** dB relative to the reference loudness; positive means louder than the reference. Null when unknown. */
    val loudnessDb: Double? = null,
)

class StreamUnavailableException(message: String) : Exception(message)

interface SearchSource {
    suspend fun searchTracks(query: String): Result<List<Track>>
}

interface StreamSource {
    suspend fun resolve(id: TrackId): Result<ResolvedStream>

    /** The player was refused (HTTP 403/410) using the last resolved stream; prefer a different client next time. */
    fun markRejected(id: TrackId) {}
}

interface RadioSource {
    /** Tracks related to [seed], at most [limit]. Failures are returned, never thrown. */
    suspend fun related(seed: Track, limit: Int): Result<List<Track>>
}

interface NetworkMonitor {
    val online: kotlinx.coroutines.flow.StateFlow<Boolean>
}

interface PlayEventRepository {
    suspend fun record(track: Track, playedAtEpochMs: Long, playTimeMs: Long)
    suspend fun playCount(id: TrackId): Int
}

interface LyricsProvider {
    val id: String
    suspend fun lyrics(track: Track): Result<String?>
}
