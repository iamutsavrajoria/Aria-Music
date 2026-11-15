// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.player

import app.aria.domain.AppSettings
import app.aria.domain.PlaybackEngine
import app.aria.domain.SettingsRepository
import app.aria.music.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Applies stored settings to the engine. Started by the Application so no screen has to exist for settings to take effect. */
@Singleton
class EngineSettingsApplier @Inject constructor(
    private val engine: PlaybackEngine,
    private val settings: SettingsRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    fun start(): Job = scope.launch { settings.settings.collect { apply(it) } }

    internal fun apply(s: AppSettings) {
        engine.setEndlessRadio(s.endlessRadio)
        engine.setSkipSilence(s.skipSilence)
        engine.setNormalizeLoudness(s.normalizeLoudness)
        engine.setHistoryThresholdMs(s.historyThresholdMs)
    }
}
