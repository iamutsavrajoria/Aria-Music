// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aria.domain.LibraryRepository
import app.aria.domain.NetworkMonitor
import app.aria.domain.SearchHistoryRepository
import app.aria.domain.SearchRepository
import app.aria.domain.SearchResults
import app.aria.domain.Track
import app.aria.domain.TrackId
import app.aria.music.player.PlayerController
import app.aria.music.player.PlayerMessage
import app.aria.music.ui.common.likedAmong
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SearchMode {
    data class Idle(val recent: List<String>) : SearchMode
    data class Suggestions(val suggestions: List<String>, val recent: List<String>) : SearchMode
    data object Loading : SearchMode
    data class Results(val results: SearchResults) : SearchMode
    data object Empty : SearchMode
    data object Error : SearchMode
    data object Offline : SearchMode
}

/** [liked] holds the ids of the visible result songs that are liked. */
data class SearchUiState(val query: String, val mode: SearchMode, val liked: Set<TrackId> = emptySet())

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val search: SearchRepository,
    private val history: SearchHistoryRepository,
    private val library: LibraryRepository,
    private val controller: PlayerController,
    private val network: NetworkMonitor,
) : ViewModel() {
    private val query = MutableStateFlow("")
    /** (generation, query). The generation lets [retry] re-run the same text. Null while the user is typing. */
    private val submitted = MutableStateFlow<Pair<Int, String>?>(null)
    private var generation = 0

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private val suggestions: Flow<List<String>> = query.debounce(DEBOUNCE_MS).distinctUntilChanged().mapLatest { q ->
        // Offline never touches the network; a failed suggestion request just shows history.
        if (q.isBlank() || !network.online.value) emptyList() else guarded { search.suggestions(q.trim()) }.getOrDefault(emptyList())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val searched: Flow<SearchMode?> = submitted.flatMapLatest { s ->
        if (s == null) flowOf(null) else flow {
            if (!network.online.value) { emit(SearchMode.Offline); return@flow }
            emit(SearchMode.Loading)
            val outcome = guarded { search.search(s.second) }
            emit(outcome.fold({ if (it.isEmpty) SearchMode.Empty else SearchMode.Results(it) }, { SearchMode.Error }))
        }
    }

    /** The search itself runs once per submit: the liked lookup hangs off this flow instead of re-collecting [searched]. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<SearchUiState> = combine(query, suggestions, history.recent(HISTORY_LIMIT), searched) { q, sugg, recent, searchMode ->
        SearchUiState(
            q,
            when {
                searchMode != null -> searchMode
                q.isBlank() -> SearchMode.Idle(recent)
                else -> SearchMode.Suggestions(sugg, recent)
            },
        )
    }.flatMapLatest { s ->
        val songs = (s.mode as? SearchMode.Results)?.results?.songs.orEmpty()
        library.likedAmong(songs).map { s.copy(liked = it) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState("", SearchMode.Idle(emptyList())))

    fun onQueryChange(q: String) { query.value = q; submitted.value = null }

    fun submit(q: String = query.value) {
        val text = q.trim()
        if (text.isEmpty()) return
        query.value = text
        submitted.value = ++generation to text
        guardedWrite(PlayerMessage.HistoryFailed) { history.add(text) }
    }

    fun retry() { submitted.value?.let { submitted.value = ++generation to it.second } }
    fun removeHistory(q: String) = guardedWrite(PlayerMessage.HistoryFailed) { history.remove(q) }
    fun clearHistory() = guardedWrite(PlayerMessage.HistoryFailed) { history.clear() }

    fun playSong(songs: List<Track>, index: Int) = controller.play(songs, index)
    fun playNext(t: Track) = controller.playNext(t)
    fun addToQueue(t: Track) = controller.addToQueue(listOf(t))
    // Liking also puts the song in the library (ADR-0003).
    fun toggleLike(t: Track, liked: Boolean) = guardedWrite(PlayerMessage.LikeFailed) { library.setLiked(t, liked) }

    private fun guardedWrite(failure: PlayerMessage, block: suspend () -> Unit) {
        viewModelScope.launch {
            try { block() } catch (e: CancellationException) { throw e } catch (e: Exception) { controller.notify(failure) }
        }
    }

    /** Repositories return failures as values, but an unexpected throw must end as a state, never as a crash of the shared flow. */
    private suspend fun <T> guarded(block: suspend () -> Result<T>): Result<T> =
        try { block() } catch (e: CancellationException) { throw e } catch (e: Exception) { Result.failure(e) }

    private companion object {
        const val DEBOUNCE_MS = 300L
        const val HISTORY_LIMIT = 20
    }
}
