// SPDX-License-Identifier: GPL-3.0-only
package app.aria.innertube.adapter

import app.aria.innertube.models.*
import app.aria.innertube.pages.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class YouTubeBrowseSourceTest {
    private val artist = ArtistItem(id = "UC1", title = "N", thumbnail = null, shuffleEndpoint = null, radioEndpoint = null)

    @Test fun failuresAreReturnedNotThrown() = runBlocking {
        val src = YouTubeBrowseSource(home = { error("net") }, album = { Result.failure(java.io.IOException("x")) })
        assertTrue(src.home().isFailure); assertTrue(src.album("a").isFailure)
    }

    @Test fun artistAndMoreMapThroughTheLambdas() = runBlocking {
        var asked: String? = null
        val src = YouTubeBrowseSource(
            artist = { asked = it; Result.success(ArtistPage(artist, emptyList(), null, null)) },
            playlistMore = { Result.success(PlaylistContinuationPage(emptyList(), "next")) },
        )
        assertEquals("UC1", src.artist("UC1").getOrThrow().artist.id); assertEquals("UC1", asked)
        assertEquals("next", src.playlistMore("c").getOrThrow().continuation)
    }
}
