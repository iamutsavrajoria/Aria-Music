// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.common

import app.aria.domain.LibraryRepository
import app.aria.domain.Track
import app.aria.domain.TrackId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * The ids among [tracks] that are liked, kept live from the library. One failing lookup reads as "not liked" instead of
 * ending the flow, so an upstream throw can never crash the shared state flow of a screen.
 * Only the first [limit] tracks are watched (one flow each): a very long list marks its first rows and leaves the rest unmarked.
 */
fun LibraryRepository.likedAmong(tracks: Collection<Track>, limit: Int = Int.MAX_VALUE): Flow<Set<TrackId>> {
    val ids = tracks.asSequence().map { it.id }.distinct().take(limit).toList()
    if (ids.isEmpty()) return flowOf(emptySet())
    return combine(ids.map { id -> isLiked(id).catch { emit(false) }.map { id to it } }) { pairs ->
        pairs.filter { it.second }.map { it.first }.toSet()
    }
}
