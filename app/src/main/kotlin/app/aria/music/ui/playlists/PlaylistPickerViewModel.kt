// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aria.domain.LibrarySort
import app.aria.domain.PlaylistRef
import app.aria.domain.PlaylistRepository
import app.aria.domain.Track
import app.aria.domain.TrackId
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex

/** Behind the "Add to playlist" dialog. Adding upserts the song but does not put it in the library (ADR-0003). */
@HiltViewModel
class PlaylistPickerViewModel @Inject constructor(private val repo: PlaylistRepository) : ViewModel() {
    val playlists: StateFlow<List<PlaylistRef>> = repo.playlists("", LibrarySort.RECENTLY_ADDED)
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    sealed interface Outcome {
        data class Added(val playlistId: String) : Outcome
        /** Another add is still running: this tap did nothing. */
        data object Busy : Outcome
        /** The name had no visible character: nothing was created. */
        data object BlankName : Outcome
    }

    /** One add at a time: a second tap while one is running is ignored, so a slow write cannot add the song twice. */
    private val busy = Mutex()

    /** A playlist created for a song whose add failed: a retry with the same name and song reuses it instead of creating a duplicate. */
    private data class Created(val name: String, val trackId: TrackId, val playlistId: String)
    private var created: Created? = null

    /** Bumped by every dismissal: a create that was already running when the dialog went away must not leave a remembered playlist behind. */
    private var epoch = 0

    /** The person closed the dialog (Cancel, Done, tap outside): a later dialog (even for the same song and name) starts fresh. Not called on rotation, which keeps the dialog. */
    fun dismissed() { epoch++; created = null }

    /** Failures throw (and leave nothing half-done that a retry would repeat). */
    suspend fun addTo(playlistId: String, track: Track): Outcome = exclusive {
        repo.addTracks(playlistId, listOf(track)); Outcome.Added(playlistId)
    }

    suspend fun createAndAdd(name: String, track: Track): Outcome {
        val clean = PlaylistNames.clean(name) ?: return Outcome.BlankName
        return exclusive {
            val startEpoch = epoch
            suspend fun createRemembered(): String = repo.create(clean).also { if (epoch == startEpoch) created = Created(clean, track.id, it) }
            val reused = created?.takeIf { it.name == clean && it.trackId == track.id }?.playlistId
            val id = if (reused == null) createRemembered() else {
                try { repo.addTracks(reused, listOf(track)); reused } catch (e: CancellationException) { throw e } catch (e: Exception) {
                    // Only a playlist that no longer exists is replaced; a transient failure keeps it for the next retry (no orphan, no duplicate).
                    if (repo.playlist(reused).first() != null) throw e
                    created = null
                    createRemembered()
                }
            }
            if (id != reused) repo.addTracks(id, listOf(track))
            created = null
            Outcome.Added(id)
        }
    }

    private suspend fun exclusive(block: suspend () -> Outcome): Outcome {
        if (!busy.tryLock()) return Outcome.Busy
        try { return block() } finally { busy.unlock() }
    }
}
