// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aria.domain.ArtistDetail
import app.aria.domain.BrowseItem
import app.aria.domain.BrowseSource
import app.aria.domain.LibraryRepository
import app.aria.domain.NetworkMonitor
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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ArtistUiState {
    data object Loading : ArtistUiState
    /** [liked] holds the ids of the songs in the song shelves that are liked. */
    data class Content(val detail: ArtistDetail, val followed: Boolean, val liked: Set<TrackId> = emptySet()) : ArtistUiState
    data object Error : ArtistUiState
    data object Offline : ArtistUiState
}

/** The songs of every shelf that lists songs only, in the order the artist page shows them. */
internal fun ArtistDetail.songShelfTracks(): List<Track> = shelves.flatMap { s -> s.items.filterIsInstance<BrowseItem.Song>().map { it.track } }

/** Artist page: loaded from the network on open and on retry (nothing is cached); offline it never touches the network. */
@HiltViewModel
class ArtistViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val browse: BrowseSource,
    private val library: LibraryRepository,
    private val controller: PlayerController,
    private val network: NetworkMonitor,
) : ViewModel() {
    private val id: String = routeId(handle, "Artist")

    private sealed interface Load {
        data object Loading : Load
        data class Done(val detail: ArtistDetail) : Load
        data object Failed : Load
        data object Offline : Load
    }

    private val load = MutableStateFlow<Load>(Load.Loading)
    private var job: Job? = null

    init { retry() }

    fun retry() {
        job?.cancel()
        job = viewModelScope.launch {
            if (!network.online.value) { load.value = Load.Offline; return@launch }
            load.value = Load.Loading
            load.value = try {
                browse.artist(id).fold({ Load.Done(it) }, { Load.Failed })
            } catch (e: CancellationException) { throw e } catch (e: Exception) { Load.Failed }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<ArtistUiState> = load.flatMapLatest { l ->
        when (l) {
            Load.Loading -> flowOf(ArtistUiState.Loading)
            Load.Failed -> flowOf(ArtistUiState.Error)
            Load.Offline -> flowOf(ArtistUiState.Offline)
            is Load.Done -> combine(
                library.isArtistFollowed(l.detail.artist.id).catch { emit(false) },
                library.likedAmong(l.detail.songShelfTracks(), LIKED_LIMIT),
            ) { followed, liked -> ArtistUiState.Content(l.detail, followed, liked) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ArtistUiState.Loading)

    private fun top(): List<Track> = (load.value as? Load.Done)?.detail?.topTracks.orEmpty()

    fun playTop() = controller.play(top(), 0)
    fun shuffleTop() = controller.play(top().shuffled(), 0)
    fun playShelfSongs(tracks: List<Track>, index: Int) = controller.play(tracks, index)

    fun toggleFollowed() {
        val c = state.value as? ArtistUiState.Content ?: return
        guardedWrite(controller) { library.setArtistFollowed(c.detail.artist, !c.followed) }
    }

    fun trackAction(t: Track, a: TrackMenuAction) = runTrackAction(library, controller, t, a)

    private companion object { const val LIKED_LIMIT = 300 }
}
