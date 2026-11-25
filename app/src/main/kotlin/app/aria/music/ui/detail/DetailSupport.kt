// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aria.domain.LibraryRepository
import app.aria.domain.Track
import app.aria.music.player.PlayerController
import app.aria.music.player.PlayerMessage
import app.aria.music.ui.common.TrackMenuAction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Runs a library write without ever throwing into the UI: a failure becomes the "could not update your library" message. */
internal fun ViewModel.guardedWrite(controller: PlayerController, block: suspend () -> Unit) {
    viewModelScope.launch {
        try { block() } catch (e: CancellationException) { throw e } catch (e: Exception) { controller.notify(PlayerMessage.LikeFailed) }
    }
}

/** The song menu shared by the three detail pages. Liking also puts the song in the library (ADR-0003). */
internal fun ViewModel.runTrackAction(library: LibraryRepository, controller: PlayerController, track: Track, action: TrackMenuAction) {
    when (action) {
        TrackMenuAction.PLAY_NEXT -> controller.playNext(track)
        TrackMenuAction.ADD_TO_QUEUE -> controller.addToQueue(listOf(track))
        TrackMenuAction.LIKE -> guardedWrite(controller) { library.setLiked(track, true) }
        TrackMenuAction.UNLIKE -> guardedWrite(controller) { library.setLiked(track, false) }
    }
}

/** The route argument; a missing one is a programming error, an unknown id is just a failed load. */
internal fun routeId(handle: androidx.lifecycle.SavedStateHandle, page: String): String =
    checkNotNull(handle.get<String>("id")) { "$page route id missing" }
