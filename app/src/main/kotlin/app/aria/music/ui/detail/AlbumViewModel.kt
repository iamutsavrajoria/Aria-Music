// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aria.domain.AlbumDetail
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface AlbumUiState {
    data object Loading : AlbumUiState
    /** [liked] holds the ids of the album's songs that are liked. */
    data class Content(val detail: AlbumDetail, val saved: Boolean, val liked: Set<TrackId> = emptySet()) : AlbumUiState
    data object Error : AlbumUiState
    data object Offline : AlbumUiState
}

/** Album page: loaded from the network on open and on retry (nothing is cached); offline it never touches the network. */
@HiltViewModel
class AlbumViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val browse: BrowseSource,
    private val library: LibraryRepository,
    private val controller: PlayerController,
    private val network: NetworkMonitor,
) : ViewModel() {
    private val id: String = routeId(handle, "Album")

    private sealed interface Load {
        data object Loading : Load
        data class Done(val detail: AlbumDetail) : Load
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
                browse.album(id).fold({ Load.Done(it) }, { Load.Failed })
            } catch (e: CancellationException) { throw e } catch (e: Exception) { Load.Failed }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<AlbumUiState> = load.flatMapLatest { l ->
        when (l) {
            Load.Loading -> flowOf(AlbumUiState.Loading)
            Load.Failed -> flowOf(AlbumUiState.Error)
            Load.Offline -> flowOf(AlbumUiState.Offline)
            is Load.Done -> combine(
                library.isAlbumSaved(l.detail.album.id).catch { emit(false) },
                library.likedAmong(l.detail.tracks),
            ) { saved, liked -> AlbumUiState.Content(l.detail, saved, liked) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlbumUiState.Loading)

    private fun tracks(): List<Track> = (load.value as? Load.Done)?.detail?.tracks.orEmpty()

    /** [PlayerController.play] windows a list above the queue cap around the tapped song and says so. */
    fun play(index: Int = 0) = controller.play(tracks(), index)
    fun shuffle() = controller.play(tracks().shuffled(), 0)
    fun addToQueue() { tracks().let { if (it.isNotEmpty()) controller.addToQueue(it) } }

    private val writes = Mutex()

    /** Flips the stored value: it is read inside the write (one at a time), so a double tap or a stale screen cannot set the same value twice. */
    fun toggleSaved() {
        val album = (load.value as? Load.Done)?.detail?.album ?: return
        guardedWrite(controller) { writes.withLock { library.setAlbumSaved(album, !library.isAlbumSaved(album.id).first()) } }
    }

    fun trackAction(t: Track, a: TrackMenuAction) = runTrackAction(library, controller, t, a)
}
