// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music

import android.content.Context
import app.aria.domain.TrackId
import app.aria.playback.ResumeStore

/** Resume positions are not secrets, so plain SharedPreferences is fine (the `secure/` area is for tokens). */
class SharedPrefsResumeStore(context: Context) : ResumeStore {
    private val prefs = context.getSharedPreferences("resume", Context.MODE_PRIVATE)
    override fun save(id: TrackId, positionMs: Long) { prefs.edit().putLong(id.raw, positionMs).apply() }
    override fun load(id: TrackId): Long = prefs.getLong(id.raw, 0L)
}
