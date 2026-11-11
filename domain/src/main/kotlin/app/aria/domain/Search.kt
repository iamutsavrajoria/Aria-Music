// SPDX-License-Identifier: GPL-3.0-only
package app.aria.domain

import kotlinx.coroutines.flow.Flow

data class SearchResults(
    val songs: List<Track>,
    val albums: List<AlbumRef>,
    val artists: List<ArtistRef>,
    val playlists: List<PlaylistRef>,
) {
    val isEmpty: Boolean get() = songs.isEmpty() && albums.isEmpty() && artists.isEmpty() && playlists.isEmpty()
}

/** Remote search. Implemented in :innertube. */
interface SearchRepository {
    suspend fun suggestions(query: String): Result<List<String>>
    suspend fun search(query: String): Result<SearchResults>
}

/** Local, clearable search history (table `search_history`). Newest first. */
interface SearchHistoryRepository {
    fun recent(limit: Int): Flow<List<String>>
    suspend fun add(query: String)
    suspend fun remove(query: String)
    suspend fun clear()
}
