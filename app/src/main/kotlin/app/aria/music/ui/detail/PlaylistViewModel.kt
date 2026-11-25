// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aria.domain.BrowseSource
import app.aria.domain.LibraryRepository
import app.aria.domain.NetworkMonitor
import app.aria.domain.PlaylistDetail
import app.aria.domain.Track
import app.aria.domain.TrackId
import app.aria.music.player.PlayerController
import app.aria.music.ui.common.TrackMenuAction
import app.aria.music.ui.common.likedAmong
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface PlaylistUiState {
    data object Loading : PlaylistUiState
    /** [liked] holds the ids of the loaded songs (the first 300) that are liked. */
    data class Content(val detail: PlaylistDetail, val loadingMore: Boolean, val moreFailed: Boolean, val liked: Set<TrackId> = emptySet()) : PlaylistUiState
    data object Error : PlaylistUiState
    data object Offline : PlaylistUiState
}

/** Remote playlist page: loaded from the network, with further pages fetched on demand (no cache, no paging library). */
@HiltViewModel
class PlaylistViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val browse: BrowseSource,
    private val library: LibraryRepository,
    private val controller: PlayerController,
    private val network: NetworkMonitor,
) : ViewModel() {
    private val id: String = routeId(handle, "Playlist")

    /** The page as loaded; [state] adds the liked marks on top. */
    private val core = MutableStateFlow<PlaylistUiState>(PlaylistUiState.Loading)
    private var job: Job? = null
    private var moreJob: Job? = null

    init { retry() }

    fun retry() {
        job?.cancel(); moreJob?.cancel()
        job = viewModelScope.launch {
            if (!network.online.value) { core.value = PlaylistUiState.Offline; return@launch }
            core.value = PlaylistUiState.Loading
            core.value = try {
                browse.playlist(id).fold({ PlaylistUiState.Content(it, loadingMore = false, moreFailed = false) }, { PlaylistUiState.Error })
            } catch (e: CancellationException) { throw e } catch (e: Exception) { PlaylistUiState.Error }
        }
    }

    /** Fetches the next page. One at a time; a page's repeats are dropped; a failure keeps the loaded songs and shows an inline Retry. */
    fun loadMore() {
        val c = core.value as? PlaylistUiState.Content ?: return
        val token = c.detail.continuation ?: return
        if (c.loadingMore) return
        core.value = c.copy(loadingMore = true, moreFailed = false)
        moreJob = viewModelScope.launch {
            val result = try { browse.playlistMore(token) } catch (e: CancellationException) { throw e } catch (e: Exception) { Result.failure(e) }
            // The page may have been reloaded meanwhile (retry cancels this job, but only [core] says what is current).
            val now = core.value as? PlaylistUiState.Content ?: return@launch
            core.value = result.fold(
                { page ->
                    val known = now.detail.tracks.mapTo(HashSet()) { it.id }
                    PlaylistUiState.Content(now.detail.copy(tracks = now.detail.tracks + page.tracks.filter { known.add(it.id) }, continuation = page.continuation), loadingMore = false, moreFailed = false)
                },
                { now.copy(loadingMore = false, moreFailed = true) },
            )
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val liked = core.map { (it as? PlaylistUiState.Content)?.detail?.tracks.orEmpty() }.distinctUntilChanged()
        .flatMapLatest { if (it.isEmpty()) flowOf(emptySet()) else library.likedAmong(it, LIKED_LIMIT) }

    val state: StateFlow<PlaylistUiState> = combine(core, liked) { s, l -> if (s is PlaylistUiState.Content) s.copy(liked = l) else s }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlaylistUiState.Loading)

    private fun tracks(): List<Track> = (core.value as? PlaylistUiState.Content)?.detail?.tracks.orEmpty()

    /** Play, Shuffle and Add to queue use the songs loaded so far; [PlayerController] windows a list above the queue cap. */
    fun play(index: Int = 0) = controller.play(tracks(), index)
    fun shuffle() = controller.play(tracks().shuffled(), 0)
    fun addToQueue() { tracks().let { if (it.isNotEmpty()) controller.addToQueue(it) } }

    fun trackAction(t: Track, a: TrackMenuAction) = runTrackAction(library, controller, t, a)

    private companion object { const val LIKED_LIMIT = 300 }
}
