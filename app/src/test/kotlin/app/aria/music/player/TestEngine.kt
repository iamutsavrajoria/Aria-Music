// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.player

import app.aria.domain.*
import app.aria.domain.testing.FakeNetworkMonitor
import app.aria.playback.DefaultPlaybackEngine
import kotlinx.coroutines.CoroutineDispatcher

/** A real engine with inert collaborators and no output attached (no service running). */
fun testEngine(dispatcher: CoroutineDispatcher) = DefaultPlaybackEngine(
    streams = object : StreamSource { override suspend fun resolve(id: TrackId) = Result.failure<ResolvedStream>(IllegalStateException("test")) },
    store = object : QueueStore {
        override suspend fun saveStructure(queue: SavedQueue) {}
        override suspend fun savePosition(position: SavedPosition) {}
        override suspend fun load(): RestoredQueue? = null
    },
    radio = object : RadioSource { override suspend fun related(seed: Track, limit: Int) = Result.success(emptyList<Track>()) },
    history = object : PlayEventRepository {
        override suspend fun record(track: Track, playedAtEpochMs: Long, playTimeMs: Long) {}
        override suspend fun playCount(id: TrackId) = 0
    },
    network = FakeNetworkMonitor(),
    dispatcher = dispatcher,
    workDispatcher = dispatcher,
)
