// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aria.domain.HomeRepository
import app.aria.domain.LibraryRepository
import app.aria.domain.NetworkMonitor
import app.aria.domain.ShelfState
import app.aria.domain.Track
import app.aria.domain.TrackId
import app.aria.music.player.PlayerController
import app.aria.music.player.PlayerMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface RemoteSection {
    data object Loading : RemoteSection
    data class Content(val shelves: List<ShelfState>, val outdated: Boolean, val refreshFailed: Boolean) : RemoteSection
    data object Error : RemoteSection
    data object Offline : RemoteSection
}

/** [liked] holds the ids of the visible local songs (quick picks and recently played) that are liked. */
data class HomeUiState(
    val quickPicks: List<Track>,
    val recent: List<Track>,
    val remote: RemoteSection,
    val refreshing: Boolean,
    val liked: Set<TrackId> = emptySet(),
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val home: HomeRepository,
    private val library: LibraryRepository,
    private val controller: PlayerController,
    private val network: NetworkMonitor,
) : ViewModel() {
    private enum class Phase { Idle, Running, Failed }

    private val phase = MutableStateFlow(Phase.Idle)
    /** The shelves of the last successful refresh in this session, failed shelves included. Wins over the cache, which holds Ready shelves only. */
    private val fresh = MutableStateFlow<List<ShelfState>?>(null)

    init {
        // Open, and again when the connection returns while nothing is shown yet: refresh only when the cache is missing or old.
        viewModelScope.launch {
            network.online.collect { online ->
                val idle = phase.value != Phase.Running && (fresh.value == null || phase.value == Phase.Failed)
                if (online && idle && safely { home.needsRevalidation() } != false) startRefresh(manual = false)
            }
        }
    }

    /** Pull to refresh and Retry. Offline it never touches the network and says so. */
    fun refresh() {
        if (phase.value == Phase.Running) return
        if (!network.online.value) { controller.notify(PlayerMessage.RefreshFailed); return }
        startRefresh(manual = true)
    }

    private fun startRefresh(manual: Boolean) {
        if (phase.value == Phase.Running || !network.online.value) return
        phase.value = Phase.Running
        viewModelScope.launch {
            val result = try { home.refresh() } catch (e: CancellationException) { throw e } catch (e: Exception) { Result.failure(e) }
            result.fold(
                { fresh.value = it.shelves; phase.value = Phase.Idle },
                {
                    phase.value = Phase.Failed
                    // The automatic revalidation is silent: the stale banner and the cached shelves already tell the story.
                    if (manual) controller.notify(PlayerMessage.RefreshFailed)
                },
            )
        }
    }

    private val remoteSection: Flow<RemoteSection> = combine(home.remote(), fresh, phase, network.online) { cache, freshShelves, p, online ->
        val shelves = freshShelves ?: cache?.shelves?.map { ShelfState.Ready(it) }
        when {
            shelves != null -> RemoteSection.Content(shelves, outdated = freshShelves == null && cache?.outdated == true, refreshFailed = p == Phase.Failed)
            !online -> RemoteSection.Offline
            p == Phase.Failed -> RemoteSection.Error
            else -> RemoteSection.Loading
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val liked: Flow<Set<TrackId>> = combine(home.quickPicks(LIMIT), home.recentlyPlayed(LIMIT)) { q, r -> (q + r).map { it.id }.toSet() }
        .flatMapLatest { ids ->
            if (ids.isEmpty()) flowOf(emptySet()) else combine(ids.map { id -> library.isLiked(id).catch { emit(false) }.map { id to it } }) { pairs ->
                pairs.filter { it.second }.map { it.first }.toSet()
            }
        }

    val state: StateFlow<HomeUiState> = combine(
        combine(home.quickPicks(LIMIT), home.recentlyPlayed(LIMIT), liked) { q, r, l -> Triple(q, r, l) },
        remoteSection, phase,
    ) { (q, r, l), remote, p ->
        HomeUiState(q, r, remote, refreshing = p == Phase.Running && remote is RemoteSection.Content, liked = l)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState(emptyList(), emptyList(), RemoteSection.Loading, false))

    fun play(tracks: List<Track>, index: Int) = controller.play(tracks, index)
    fun playNext(t: Track) = controller.playNext(t)
    fun addToQueue(t: Track) = controller.addToQueue(listOf(t))
    // Liking also puts the song in the library (ADR-0003).
    fun like(t: Track) = setLiked(t, true)
    fun unlike(t: Track) = setLiked(t, false)

    private fun setLiked(t: Track, liked: Boolean) {
        viewModelScope.launch {
            try { library.setLiked(t, liked) } catch (e: CancellationException) { throw e } catch (e: Exception) { controller.notify(PlayerMessage.LikeFailed) }
        }
    }

    private suspend fun <T> safely(block: suspend () -> T): T? =
        try { block() } catch (e: CancellationException) { throw e } catch (e: Exception) { null }

    private companion object { const val LIMIT = 12 }
}
