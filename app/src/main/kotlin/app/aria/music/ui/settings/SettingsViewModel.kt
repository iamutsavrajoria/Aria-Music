// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aria.domain.AppSettings
import app.aria.domain.NowPlayingSkin
import app.aria.domain.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface SettingsUiState {
    data object Loading : SettingsUiState
    data class Content(val settings: AppSettings) : SettingsUiState
}

object SettingsChoices {
    val SLEEP_MINUTES = listOf(5, 10, 15, 30, 45, 60, 90)
    const val HISTORY_STEP_S = 5
    const val HISTORY_MIN_S = (AppSettings.MIN_HISTORY_THRESHOLD_MS / 1_000).toInt()
    const val HISTORY_MAX_S = (AppSettings.MAX_HISTORY_THRESHOLD_MS / 1_000).toInt()
}

/**
 * Edits [SettingsRepository] and nothing else: applying the values to the engine is the Application's job.
 * Every change is a transform of the stored value, which the repository normalizes (clamps), and the onboarding flag is never touched here.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(private val repo: SettingsRepository) : ViewModel() {
    val state: StateFlow<SettingsUiState> = repo.settings.map<AppSettings, SettingsUiState> { SettingsUiState.Content(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState.Loading)

    private fun edit(f: (AppSettings) -> AppSettings) { viewModelScope.launch { repo.update(f) } }

    fun setHistorySeconds(seconds: Int) = edit { it.copy(historyThresholdMs = seconds * 1_000L) }
    fun setEndlessRadio(on: Boolean) = edit { it.copy(endlessRadio = on) }
    fun setSkipSilence(on: Boolean) = edit { it.copy(skipSilence = on) }
    fun setNormalizeLoudness(on: Boolean) = edit { it.copy(normalizeLoudness = on) }
    fun setSleepDefaultMinutes(minutes: Int) = edit { it.copy(sleepTimerDefaultMinutes = minutes) }
    fun setNowPlayingSkin(skin: NowPlayingSkin) = edit { it.copy(nowPlayingSkin = skin) }
}
