// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aria.domain.LibrarySort
import app.aria.domain.PlaylistRef
import app.aria.domain.PlaylistRepository
import app.aria.domain.Track
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn

/** Behind the "Add to playlist" dialog. Adding upserts the song but does not put it in the library (ADR-0003). */
@HiltViewModel
class PlaylistPickerViewModel @Inject constructor(private val repo: PlaylistRepository) : ViewModel() {
    val playlists: StateFlow<List<PlaylistRef>> = repo.playlists("", LibrarySort.RECENTLY_ADDED)
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun addTo(playlistId: String, track: Track) = repo.addTracks(playlistId, listOf(track))

    /** Null when the name is blank (nothing is created). */
    suspend fun create(name: String): String? = PlaylistNames.clean(name)?.let { repo.create(it) }

    suspend fun createAndAdd(name: String, track: Track): String? = create(name)?.also { repo.addTracks(it, listOf(track)) }
}
