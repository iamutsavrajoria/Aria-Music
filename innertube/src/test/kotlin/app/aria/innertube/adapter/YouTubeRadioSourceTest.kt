// SPDX-License-Identifier: GPL-3.0-only
package app.aria.innertube.adapter

import app.aria.domain.Track
import app.aria.domain.TrackId
import app.aria.innertube.models.Artist
import app.aria.innertube.models.SongItem
import app.aria.innertube.models.WatchEndpoint
import app.aria.innertube.pages.NextResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class YouTubeRadioSourceTest {
    private fun song(id: String) = SongItem(id = id, title = id, artists = listOf(Artist("A", null)), thumbnail = "t")
    private val seed = Track(TrackId.youtube("aaaaaaaaaaa"), "Seed", listOf("A"), null, null)

    @Test fun excludesSeedAndRespectsLimit() = runBlocking {
        var asked: WatchEndpoint? = null
        val src = YouTubeRadioSource { ep -> asked = ep; Result.success(NextResult(items = listOf(song("aaaaaaaaaaa"), song("bbbbbbbbbbb"), song("ccccccccccc"), song("ddddddddddd")), continuation = null, endpoint = ep)) }
        val r = src.related(seed, 2).getOrThrow()
        assertEquals(listOf("bbbbbbbbbbb", "ccccccccccc"), r.map { it.id.raw })
        assertEquals("RDAMVMaaaaaaaaaaa", asked?.playlistId); assertEquals("aaaaaaaaaaa", asked?.videoId)
    }
    @Test fun failuresAreReturned() = runBlocking {
        assertTrue(YouTubeRadioSource { Result.failure(RuntimeException("x")) }.related(seed, 5).isFailure)
    }
    @Test fun nonYouTubeSeedFails() = runBlocking {
        assertTrue(YouTubeRadioSource { error("unused") }.related(seed.copy(id = TrackId.parse("local:x")), 5).isFailure)
    }
}
