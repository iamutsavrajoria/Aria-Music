// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aria.domain.AlbumRef
import app.aria.domain.ArtistRef
import app.aria.domain.LibraryRepository
import app.aria.domain.LibrarySong
import app.aria.domain.LibrarySort
import app.aria.domain.PlaylistRef
import app.aria.domain.PlaylistRepository
import app.aria.domain.Track
import app.aria.music.player.PlayerController
import app.aria.music.player.PlayerMessage
import app.aria.music.ui.playlists.PlaylistNames
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class LibraryTab { SONGS, ALBUMS, ARTISTS, PLAYLISTS }

sealed interface LibraryContent {
    data object Loading : LibraryContent
    /** Nothing in this tab at all. */
    data object Empty : LibraryContent
    /** The filter matches nothing. */
    data object NoMatches : LibraryContent
    data object Error : LibraryContent
    data class Songs(val rows: List<LibrarySong>) : LibraryContent
    data class Albums(val rows: List<AlbumRef>) : LibraryContent
    data class Artists(val rows: List<ArtistRef>) : LibraryContent
    data class Playlists(val rows: List<PlaylistRef>) : LibraryContent
}

data class LibraryUiState(val tab: LibraryTab, val query: String, val sort: LibrarySort, val content: LibraryContent)

enum class LibraryTrackAction { PLAY_NEXT, ADD_TO_QUEUE, UNLIKE, REMOVE_FROM_LIBRARY }

/** Local library (ADR-0003): every list is a SQL-sorted, SQL-filtered Flow from the repositories; nothing here needs the network. */
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val library: LibraryRepository,
    private val playlists: PlaylistRepository,
    private val controller: PlayerController,
) : ViewModel() {
    private val tab = MutableStateFlow(LibraryTab.SONGS)
    private val query = MutableStateFlow("")
    private val sort = MutableStateFlow(LibrarySort.RECENTLY_ADDED)
    private val reload = MutableStateFlow(0)

    private data class Filter(val query: String, val sort: LibrarySort)

    /** Which load a [LibraryContent] belongs to: the tab, and the retry count that reloads the same tab. */
    private data class Load(val tab: LibraryTab, val attempt: Int)
    private data class Tagged(val load: Load, val content: LibraryContent)

    // Loading shows only when the tab (or a retry) changes; typing and sorting swap the rows in place, without flashing the spinner.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val content: Flow<Tagged> = combine(tab, reload) { t, r -> Load(t, r) }.flatMapLatest { load ->
        combine(query, sort) { q, s -> Filter(q, s) }.flatMapLatest { f ->
            rows(load.tab, f)
                .map { c -> if (c.isEmpty()) (if (f.query.isEmpty()) LibraryContent.Empty else LibraryContent.NoMatches) else c }
                .catch { emit(LibraryContent.Error) }
        }.onStart { emit(LibraryContent.Loading) }.map { Tagged(load, it) }
    }

    private fun rows(t: LibraryTab, f: Filter): Flow<LibraryContent> = when (t) {
        LibraryTab.SONGS -> library.songs(f.query, f.sort).map { LibraryContent.Songs(it) }
        LibraryTab.ALBUMS -> library.albums(f.query, f.sort).map { LibraryContent.Albums(it) }
        LibraryTab.ARTISTS -> library.artists(f.query, f.sort).map { LibraryContent.Artists(it) }
        LibraryTab.PLAYLISTS -> playlists.playlists(f.query, f.sort).map { LibraryContent.Playlists(it) }
    }

    // Content from a previous tab (or attempt) never pairs with the new tab: until the new load reports, the state says Loading.
    val state: StateFlow<LibraryUiState> = combine(tab, reload, query, sort, content) { t, r, q, s, tagged ->
        LibraryUiState(t, q, s, if (tagged.load == Load(t, r)) tagged.content else LibraryContent.Loading)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState(LibraryTab.SONGS, "", LibrarySort.RECENTLY_ADDED, LibraryContent.Loading))

    /** Selecting the tab that is already open reloads it, which is how the error state retries. */
    fun setTab(t: LibraryTab) { if (tab.value == t) reload.value++ else tab.value = t }
    /** The filter is trimmed: a blank or padded filter must not reach SQL or turn a non-empty tab into Empty/NoMatches. */
    fun setQuery(q: String) { query.value = q.trim() }
    fun setSort(s: LibrarySort) { sort.value = s }

    /** [PlayerController.play] windows the list to the queue cap and tells the user when it did. */
    fun play(tracks: List<Track>, index: Int) = controller.play(tracks, index)
    private fun visibleTracks(): List<Track> = (state.value.content as? LibraryContent.Songs)?.rows?.map { it.track }.orEmpty()
    fun playAll() { visibleTracks().let { if (it.isNotEmpty()) controller.play(it, 0) } }
    fun shuffleAll() { visibleTracks().let { if (it.isNotEmpty()) controller.play(it.shuffled(), 0) } }

    /** A blank name is ignored (the dialog does not offer it either); the new playlist appears through the Playlists flow. */
    fun createPlaylist(name: String) {
        val n = PlaylistNames.clean(name) ?: return
        guardedWrite(PlayerMessage.PlaylistFailed) { playlists.create(n) }
    }

    fun trackAction(t: Track, a: LibraryTrackAction) {
        when (a) {
            LibraryTrackAction.PLAY_NEXT -> controller.playNext(t)
            LibraryTrackAction.ADD_TO_QUEUE -> controller.addToQueue(listOf(t))
            LibraryTrackAction.UNLIKE -> guardedWrite { library.setLiked(t, false) }
            LibraryTrackAction.REMOVE_FROM_LIBRARY -> guardedWrite { library.setInLibrary(t, false) }
        }
    }

    private fun guardedWrite(failure: PlayerMessage = PlayerMessage.LikeFailed, block: suspend () -> Unit) {
        viewModelScope.launch {
            try { block() } catch (e: CancellationException) { throw e } catch (e: Exception) { controller.notify(failure) }
        }
    }
}

private fun LibraryContent.isEmpty() = when (this) {
    is LibraryContent.Songs -> rows.isEmpty()
    is LibraryContent.Albums -> rows.isEmpty()
    is LibraryContent.Artists -> rows.isEmpty()
    is LibraryContent.Playlists -> rows.isEmpty()
    else -> false
}
