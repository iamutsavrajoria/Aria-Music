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
)

class StreamUnavailableException(message: String) : Exception(message)

interface SearchSource {
    suspend fun searchTracks(query: String): Result<List<Track>>
}

interface StreamSource {
    suspend fun resolve(id: TrackId): Result<ResolvedStream>
}

interface PlayEventRepository {
    suspend fun record(track: Track, playedAtEpochMs: Long, playTimeMs: Long)
    suspend fun playCount(id: TrackId): Int
}

interface LyricsProvider {
    val id: String
    suspend fun lyrics(track: Track): Result<String?>
}
