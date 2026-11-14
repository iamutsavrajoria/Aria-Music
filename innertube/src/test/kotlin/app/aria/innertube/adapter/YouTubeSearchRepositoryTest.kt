// SPDX-License-Identifier: GPL-3.0-only
package app.aria.innertube.adapter

import app.aria.innertube.models.*
import app.aria.innertube.pages.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class YouTubeSearchRepositoryTest {
    @Test fun searchMapsAllSummariesIntoOneResult() = runBlocking {
        val song = SongItem(id = "dQw4w9WgXcQ", title = "S", artists = emptyList(), thumbnail = "t")
        val repo = YouTubeSearchRepository(searchSummary = { Result.success(SearchSummaryPage(listOf(SearchSummary("Songs", listOf(song)), SearchSummary("Top result", listOf(song))))) })
        assertEquals(1, repo.search("q").getOrThrow().songs.size)
    }
    @Test fun suggestionsReturnQueriesOnly() = runBlocking {
        val repo = YouTubeSearchRepository(suggestions = { Result.success(SearchSuggestions(listOf("a", "b"), emptyList())) })
        assertEquals(listOf("a", "b"), repo.suggestions("x").getOrThrow())
    }
    @Test fun failuresAreReturned() = runBlocking {
        assertTrue(YouTubeSearchRepository(searchSummary = { Result.failure(RuntimeException()) }).search("q").isFailure)
    }
}
