// SPDX-License-Identifier: GPL-3.0-only
package app.aria.domain.testing

import app.aria.domain.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

class FakeNetworkMonitor(initial: Boolean = true) : NetworkMonitor {
    override val online = MutableStateFlow(initial)
}

class FakeBrowseSource : BrowseSource {
    var homeResult: Result<List<ShelfState>> = Result.success(emptyList())
    val albums = mutableMapOf<String, Result<AlbumDetail>>()
    val artists = mutableMapOf<String, Result<ArtistDetail>>()
    val playlists = mutableMapOf<String, Result<PlaylistDetail>>()
    val more = mutableMapOf<String, Result<TrackPage>>()
    var homeCalls = 0
    override suspend fun home(): Result<List<ShelfState>> { homeCalls++; return homeResult }
    override suspend fun album(id: String) = albums[id] ?: Result.failure(NoSuchElementException(id))
    override suspend fun artist(id: String) = artists[id] ?: Result.failure(NoSuchElementException(id))
    override suspend fun playlist(id: String) = playlists[id] ?: Result.failure(NoSuchElementException(id))
    override suspend fun playlistMore(continuation: String) = more[continuation] ?: Result.failure(NoSuchElementException(continuation))
}

class FakeSearchRepository : SearchRepository {
    var suggestionResult: Result<List<String>> = Result.success(emptyList())
    var searchResult: Result<SearchResults> = Result.success(SearchResults(emptyList(), emptyList(), emptyList(), emptyList()))
    val suggestionQueries = mutableListOf<String>()
    val searchQueries = mutableListOf<String>()
    override suspend fun suggestions(query: String): Result<List<String>> { suggestionQueries += query; return suggestionResult }
    override suspend fun search(query: String): Result<SearchResults> { searchQueries += query; return searchResult }
}

class FakeSearchHistoryRepository(initial: List<String> = emptyList()) : SearchHistoryRepository {
    val items = MutableStateFlow(initial)
    override fun recent(limit: Int): Flow<List<String>> = items.map { it.take(limit) }
    override suspend fun add(query: String) { items.value = listOf(query) + items.value.filter { it != query } }
    override suspend fun remove(query: String) { items.value = items.value.filter { it != query } }
    override suspend fun clear() { items.value = emptyList() }
}

class FakeSettingsRepository(initial: AppSettings = AppSettings()) : SettingsRepository {
    private val state = MutableStateFlow(initial.normalized())
    override val settings: Flow<AppSettings> = state
    val current: AppSettings get() = state.value
    override suspend fun update(transform: (AppSettings) -> AppSettings) { state.value = transform(state.value).normalized() }
}

class FakeHomeRepository : HomeRepository {
    val quick = MutableStateFlow<List<Track>>(emptyList())
    val recent = MutableStateFlow<List<Track>>(emptyList())
    val remoteState = MutableStateFlow<HomeRemote?>(null)
    var refreshResult: Result<HomeRefresh> = Result.success(HomeRefresh(emptyList()))
    var revalidate = true
    var refreshCalls = 0
    override fun quickPicks(limit: Int): Flow<List<Track>> = quick
    override fun recentlyPlayed(limit: Int): Flow<List<Track>> = recent
    override fun remote(): Flow<HomeRemote?> = remoteState
    override suspend fun needsRevalidation() = revalidate
    override suspend fun refresh(): Result<HomeRefresh> { refreshCalls++; return refreshResult }
}

class FakeLibraryRepository : LibraryRepository {
    private data class Entry(val track: Track, val liked: Boolean, val inLibrary: Boolean, val seq: Int)
    private val songsState = MutableStateFlow<Map<TrackId, Entry>>(emptyMap())
    private val albumsState = MutableStateFlow<List<AlbumRef>>(emptyList())
    private val artistsState = MutableStateFlow<List<ArtistRef>>(emptyList())
    private var seq = 0

    private fun match(text: String, q: String) = q.isBlank() || text.contains(q, ignoreCase = true)

    override fun songs(query: String, sort: LibrarySort): Flow<List<LibrarySong>> = songsState.map { m ->
        val rows = m.values.filter { it.inLibrary && (match(it.track.title, query) || it.track.artists.any { a -> match(a, query) }) }
        val sorted = if (sort == LibrarySort.TITLE) rows.sortedBy { it.track.title.lowercase() } else rows.sortedByDescending { it.seq }
        sorted.map { LibrarySong(it.track, it.liked) }
    }
    override fun albums(query: String, sort: LibrarySort): Flow<List<AlbumRef>> = albumsState.map { l ->
        l.filter { match(it.title, query) }.let { if (sort == LibrarySort.TITLE) it.sortedBy { a -> a.title.lowercase() } else it.reversed() }
    }
    override fun artists(query: String, sort: LibrarySort): Flow<List<ArtistRef>> = artistsState.map { l ->
        l.filter { match(it.name, query) }.let { if (sort == LibrarySort.TITLE) it.sortedBy { a -> a.name.lowercase() } else it.reversed() }
    }
    override fun isLiked(id: TrackId) = songsState.map { it[id]?.liked == true }
    override fun isInLibrary(id: TrackId) = songsState.map { it[id]?.inLibrary == true }
    override fun isAlbumSaved(id: String) = albumsState.map { l -> l.any { it.id == id } }
    override fun isArtistFollowed(id: String) = artistsState.map { l -> l.any { it.id == id } }

    override suspend fun setLiked(track: Track, liked: Boolean) {
        val old = songsState.value[track.id]
        val inLib = if (liked) true else old?.inLibrary == true
        songsState.value = songsState.value + (track.id to Entry(track, liked, inLib, old?.seq ?: ++seq))
    }
    override suspend fun setInLibrary(track: Track, inLibrary: Boolean) {
        val old = songsState.value[track.id]
        songsState.value = songsState.value + (track.id to Entry(track, if (inLibrary) old?.liked == true else false, inLibrary, old?.seq ?: ++seq))
    }
    override suspend fun setAlbumSaved(album: AlbumRef, saved: Boolean) {
        albumsState.value = albumsState.value.filter { it.id != album.id } + if (saved) listOf(album) else emptyList()
    }
    override suspend fun setArtistFollowed(artist: ArtistRef, followed: Boolean) {
        artistsState.value = artistsState.value.filter { it.id != artist.id } + if (followed) listOf(artist) else emptyList()
    }
}

class FakePlaylistRepository : PlaylistRepository {
    private class P(val ref: PlaylistRef, val rows: MutableList<PlaylistTrackRow> = mutableListOf())
    private val state = MutableStateFlow<List<P>>(emptyList())
    private val tick = MutableStateFlow(0)
    private var nextId = 1
    private var nextEntry = 1
    private fun bump() { tick.value++ }
    private fun refOf(p: P) = p.ref.copy(trackCount = p.rows.size)

    override fun playlists(query: String, sort: LibrarySort): Flow<List<PlaylistRef>> = tick.map {
        state.value.map(::refOf).filter { query.isBlank() || it.title.contains(query, ignoreCase = true) }
            .let { if (sort == LibrarySort.TITLE) it.sortedBy { r -> r.title.lowercase() } else it.reversed() }
    }
    override fun playlist(id: String): Flow<PlaylistRef?> = tick.map { state.value.firstOrNull { it.ref.id == id }?.let(::refOf) }
    override fun tracks(playlistId: String): Flow<List<PlaylistTrackRow>> = tick.map { state.value.firstOrNull { it.ref.id == playlistId }?.rows?.toList() ?: emptyList() }
    override suspend fun create(name: String): String {
        val id = "LP${nextId++}"
        state.value = state.value + P(PlaylistRef(id, name, null, null, 0)); bump(); return id
    }
    override suspend fun rename(id: String, name: String) {
        state.value = state.value.map { if (it.ref.id == id) P(it.ref.copy(title = name), it.rows) else it }; bump()
    }
    override suspend fun delete(id: String) { state.value = state.value.filter { it.ref.id != id }; bump() }
    override suspend fun addTracks(id: String, tracks: List<Track>) {
        state.value.firstOrNull { it.ref.id == id }?.rows?.addAll(tracks.map { PlaylistTrackRow(nextEntry++, it) }); bump()
    }
    override suspend fun removeEntry(playlistId: String, entryId: Int) {
        state.value.firstOrNull { it.ref.id == playlistId }?.rows?.removeAll { it.entryId == entryId }; bump()
    }
    override suspend fun reorder(playlistId: String, orderedEntryIds: List<Int>) {
        val p = state.value.first { it.ref.id == playlistId }
        require(orderedEntryIds.sorted() == p.rows.map { it.entryId }.sorted()) { "entry ids do not match" }
        val byId = p.rows.associateBy { it.entryId }
        p.rows.clear(); p.rows.addAll(orderedEntryIds.map { byId.getValue(it) }); bump()
    }
}

class FakeHomeShelfCache : HomeShelfCache {
    val state = MutableStateFlow<CachedHome?>(null)
    var replaceCalls = 0
    override fun observe(): Flow<CachedHome?> = state
    override suspend fun replace(shelves: List<Shelf>, fetchedAtEpochMs: Long) { replaceCalls++; state.value = CachedHome(shelves, fetchedAtEpochMs) }
}

class FakeHomeLocalSource : HomeLocalSource {
    val quick = MutableStateFlow<List<Track>>(emptyList()); val recent = MutableStateFlow<List<Track>>(emptyList())
    override fun quickPicks(limit: Int): Flow<List<Track>> = quick
    override fun recentlyPlayed(limit: Int): Flow<List<Track>> = recent
}
