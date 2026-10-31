// SPDX-License-Identifier: GPL-3.0-only
package app.aria.playback

import app.aria.domain.PlayEventRepository
import app.aria.domain.ResolvedStream
import app.aria.domain.StreamSource
import app.aria.domain.StreamUnavailableException
import app.aria.domain.Track
import app.aria.domain.TrackId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerControllerTest {
    private val track = Track(TrackId.youtube("dQw4w9WgXcQ"), "Song", listOf("A"), 200_000L, null)

    private class FakePlayer : Player {
        var played: Pair<String, Long>? = null
        var position = 0L
        var stopped = false
        override fun play(url: String, startPositionMs: Long) { played = url to startPositionMs }
        override fun positionMs() = position
        override fun stop() { stopped = true }
    }

    private class FakeResume(var saved: Long = 0L) : ResumeStore {
        override fun save(id: TrackId, positionMs: Long) { saved = positionMs }
        override fun load(id: TrackId) = saved
    }

    private class FakeEvents : PlayEventRepository {
        val recorded = mutableListOf<Triple<Track, Long, Long>>()
        override suspend fun record(track: Track, playedAtEpochMs: Long, playTimeMs: Long) {
            recorded += Triple(track, playedAtEpochMs, playTimeMs)
        }
        override suspend fun playCount(id: TrackId) = recorded.size
    }

    private fun streams(result: Result<ResolvedStream>) = object : StreamSource {
        override suspend fun resolve(id: TrackId) = result
    }

    private val stream = ResolvedStream("https://example.invalid/a", "audio/webm", 160_000, 10L, null)

    @Test fun startPlaysAtSavedResumePosition() = runTest {
        val player = FakePlayer()
        val c = PlayerController(streams(Result.success(stream)), FakeEvents(), player, FakeResume(12_345L)) { 0L }
        assertTrue(c.start(track).isSuccess)
        assertEquals("https://example.invalid/a" to 12_345L, player.played)
    }

    @Test fun failedResolveDoesNotTouchPlayer() = runTest {
        val player = FakePlayer()
        val c = PlayerController(streams(Result.failure(StreamUnavailableException("no"))), FakeEvents(), player, FakeResume()) { 0L }
        assertTrue(c.start(track).isFailure)
        assertEquals(null, player.played)
    }

    @Test fun stopSavesPositionAndRecordsOneEvent() = runTest {
        val player = FakePlayer()
        val resume = FakeResume()
        val events = FakeEvents()
        var now = 1_000L
        val c = PlayerController(streams(Result.success(stream)), events, player, resume) { now }
        c.start(track)
        now = 31_000L
        player.position = 30_500L
        c.stopAndRecord(track)
        assertEquals(30_500L, resume.saved)
        assertEquals(1, events.recorded.size)
        assertEquals(30_000L, events.recorded.single().third)
        assertTrue(player.stopped)
    }
}
