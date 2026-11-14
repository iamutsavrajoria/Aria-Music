// SPDX-License-Identifier: GPL-3.0-only
package app.aria.innertube.adapter

import app.aria.domain.SearchRepository
import app.aria.domain.SearchResults
import app.aria.innertube.YouTube
import app.aria.innertube.models.SearchSuggestions
import app.aria.innertube.pages.SearchSummaryPage
import kotlin.coroutines.cancellation.CancellationException

class YouTubeSearchRepository(
    searchSummary: suspend (String) -> Result<SearchSummaryPage> = { YouTube.searchSummary(it) },
    suggestions: suspend (String) -> Result<SearchSuggestions> = { YouTube.searchSuggestions(it) },
) : SearchRepository {
    private val fetchSummary = searchSummary
    private val fetchSuggestions = suggestions

    override suspend fun suggestions(query: String): Result<List<String>> = try {
        fetchSuggestions(query).map { it.queries }
    } catch (e: CancellationException) { throw e } catch (e: Exception) { Result.failure(e) }

    override suspend fun search(query: String): Result<SearchResults> = try {
        fetchSummary(query).mapCatching { page -> page.summaries.flatMap { it.items }.toSearchResults() }
    } catch (e: CancellationException) { throw e } catch (e: Exception) { Result.failure(e) }
}
