// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.playlists

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aria.domain.PlaylistRef
import app.aria.domain.PlaylistRepository
import app.aria.domain.PlaylistTrackRow
import app.aria.domain.Track
import app.aria.music.player.PlayerController
import app.aria.music.player.PlayerMessage
import app.aria.music.ui.common.TrackMenuAction
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface LocalPlaylistUiState {
    data object Loading : LocalPlaylistUiState
    data class Content(val playlist: PlaylistRef, val rows: List<PlaylistTrackRow>) : LocalPlaylistUiState
    data object NotFound : LocalPlaylistUiState
    data object Error : LocalPlaylistUiState
}

/** A playlist of the user's own: every change goes through the repository and the page follows its Flows. */
@HiltViewModel
class LocalPlaylistViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val playlists: PlaylistRepository,
    private val controller: PlayerController,
) : ViewModel() {
    private val id: String = checkNotNull(handle.get<String>("id")) { "Playlist route id missing" }
    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted

    val state: StateFlow<LocalPlaylistUiState> = combine(playlists.playlist(id), playlists.tracks(id)) { p, rows ->
        if (p == null) LocalPlaylistUiState.NotFound else LocalPlaylistUiState.Content(p, rows)
    }.catch { emit(LocalPlaylistUiState.Error) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LocalPlaylistUiState.Loading)

    private fun rows(): List<PlaylistTrackRow> = (state.value as? LocalPlaylistUiState.Content)?.rows.orEmpty()

    /** Edits run one at a time, so a reorder always starts from the order the previous edit left behind. */
    private val edits = Mutex()

    private fun write(block: suspend () -> Unit) {
        viewModelScope.launch {
            try { edits.withLock { block() } } catch (e: CancellationException) { throw e } catch (e: Exception) { controller.notify(PlayerMessage.PlaylistFailed) }
        }
    }

    fun play(index: Int = 0) { val r = rows(); if (r.isNotEmpty()) controller.play(r.map { it.track }, index) }
    fun shuffle() { val r = rows(); if (r.isNotEmpty()) controller.play(r.map { it.track }.shuffled(), 0) }
    fun addToQueue() { val r = rows(); if (r.isNotEmpty()) controller.addToQueue(r.map { it.track }) }

    fun rename(name: String) { val n = PlaylistNames.clean(name) ?: return; write { playlists.rename(id, n) } }
    fun delete() = write { playlists.delete(id); _deleted.value = true }
    fun remove(entryId: Int) = write { playlists.removeEntry(id, entryId) }
    fun moveUp(entryId: Int) = move(entryId, -1)
    fun moveDown(entryId: Int) = move(entryId, +1)

    /** Computes the complete new order from the stored rows and rewrites it in one repository call (one transaction); edges and unknown ids do nothing. */
    private fun move(entryId: Int, delta: Int) = write {
        val ids = playlists.tracks(id).first().map { it.entryId }
        val from = ids.indexOf(entryId); val to = from + delta
        if (from < 0 || to !in ids.indices) return@write
        playlists.reorder(id, ids.toMutableList().apply { add(to, removeAt(from)) })
    }

    fun trackAction(t: Track, a: TrackMenuAction) {
        when (a) {
            TrackMenuAction.PLAY_NEXT -> controller.playNext(t)
            TrackMenuAction.ADD_TO_QUEUE -> controller.addToQueue(listOf(t))
            // The page's menu offers neither; liking is not this page's job.
            TrackMenuAction.LIKE, TrackMenuAction.UNLIKE -> Unit
        }
    }
}
