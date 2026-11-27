// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aria.domain.AppSettings
import app.aria.domain.SettingsRepository
import app.aria.music.player.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Holds the controller so the shell instantiates it at startup (it is a lazy singleton; early engine events must not be lost),
 * and tells the root whether first-run onboarding is still to be shown.
 */
@HiltViewModel
class ShellViewModel @Inject constructor(val controller: PlayerController, settings: SettingsRepository) : ViewModel() {
    /** Null until the stored flag has been read, so the root shows nothing (no flash of Home) while it loads. */
    val onboardingDone: StateFlow<Boolean?> = settings.settings.map<AppSettings, Boolean?> { it.onboardingDone }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
