// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aria.domain.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class OnboardingViewModel @Inject constructor(private val settings: SettingsRepository) : ViewModel() {
    /** Finishing and skipping both end here; the other settings are kept as they are. */
    fun finish() { viewModelScope.launch { settings.update { it.copy(onboardingDone = true) } } }
}
