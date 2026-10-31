// SPDX-License-Identifier: GPL-3.0-only
package app.aria.playback

import app.aria.domain.TrackId

/** In-memory ResumeStore for tests and previews. The persistent implementation lives in :app. */
class InMemoryResumeStore : ResumeStore {
    private val positions = mutableMapOf<TrackId, Long>()
    override fun save(id: TrackId, positionMs: Long) { positions[id] = positionMs }
    override fun load(id: TrackId): Long = positions[id] ?: 0L
}
