// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aria.domain.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class OnboardingViewModel @Inject constructor(private val settings: SettingsRepository) : ViewModel() {
    /** Finishing and skipping both end here; the other settings are kept as they are. */
    fun finish() {
        // Navigation pops the entry (and clears this ViewModel) right after the tap: the write must still complete. A failed write is dropped (onboarding then shows again).
        viewModelScope.launch { withContext(NonCancellable) { try { settings.update { it.copy(onboardingDone = true) } } catch (_: Exception) { } } }
    }
}
