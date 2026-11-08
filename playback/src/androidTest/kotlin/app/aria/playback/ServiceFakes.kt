// SPDX-License-Identifier: GPL-3.0-only
package app.aria.playback

import app.aria.domain.NetworkMonitor
import app.aria.domain.PlayEventRepository
import app.aria.domain.QueueStore
import app.aria.domain.RadioSource
import app.aria.domain.RestoredQueue
import app.aria.domain.ResolvedStream
import app.aria.domain.SavedPosition
import app.aria.domain.SavedQueue
import app.aria.domain.StreamSource
import app.aria.domain.StreamUnavailableException
import app.aria.domain.Track
import app.aria.domain.TrackId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// androidTest cannot see src/test, so these are small local copies of the EngineTestKit fakes.

/** Every track resolves to the same local URL, except [unavailable] ones, which fail permanently. */
class ServiceStreams(private val url: String, private val unavailable: Set<TrackId> = emptySet()) : StreamSource {
    override suspend fun resolve(id: TrackId): Result<ResolvedStream> =
        if (id in unavailable) Result.failure(StreamUnavailableException("test: permanently unavailable")) else Result.success(ResolvedStream(url, "audio/wav", 64_000, null, null))
}

class NoopStore : QueueStore {
    override suspend fun saveStructure(queue: SavedQueue) {}
    override suspend fun savePosition(position: SavedPosition) {}
    override suspend fun load(): RestoredQueue? = null
}

class NoopRadio : RadioSource {
    override suspend fun related(seed: Track, limit: Int): Result<List<Track>> = Result.success(emptyList())
}

class NoopHistory : PlayEventRepository {
    override suspend fun record(track: Track, playedAtEpochMs: Long, playTimeMs: Long) {}
    override suspend fun playCount(id: TrackId): Int = 0
}

class AlwaysOnline : NetworkMonitor {
    override val online: StateFlow<Boolean> = MutableStateFlow(true)
}
