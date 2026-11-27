// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.shell

import app.aria.domain.AppSettings
import app.aria.domain.testing.*
import app.aria.music.player.PlayerController
import app.aria.music.player.ServiceLauncher
import app.aria.music.ui.onboarding.OnboardingViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class ShellViewModelTest {
    // runTest must share this dispatcher, or backgroundScope collectors would run on a second scheduler.
    private val dispatcher = UnconfinedTestDispatcher()
    @BeforeTest fun setMain() = Dispatchers.setMain(dispatcher)
    @AfterTest fun resetMain() = Dispatchers.resetMain()

    @Test fun nullUntilTheFlagIsRead() = runTest(dispatcher) {
        val vm = ShellViewModel(PlayerController(FakePlaybackEngine(), ServiceLauncher { }, backgroundScope), FakeSettingsRepository())
        assertNull(vm.onboardingDone.value)
        backgroundScope.launch { vm.onboardingDone.collect { } }
        assertEquals(false, vm.onboardingDone.value)
    }

    @Test fun reflectsTheStoredFlag() = runTest(dispatcher) {
        val vm = ShellViewModel(PlayerController(FakePlaybackEngine(), ServiceLauncher { }, backgroundScope), FakeSettingsRepository(AppSettings(onboardingDone = true)))
        backgroundScope.launch { vm.onboardingDone.collect { } }; assertEquals(true, vm.onboardingDone.value)
    }

    @Test fun finishingOnboardingFlipsTheFlagTheShellObserves() = runTest(dispatcher) {
        val repo = FakeSettingsRepository()
        val vm = ShellViewModel(PlayerController(FakePlaybackEngine(), ServiceLauncher { }, backgroundScope), repo)
        backgroundScope.launch { vm.onboardingDone.collect { } }; assertEquals(false, vm.onboardingDone.value)
        OnboardingViewModel(repo).finish()
        assertEquals(true, vm.onboardingDone.value)
    }

    @Test fun finishingOnboardingStoresTheFlagAndKeepsOtherSettings() = runTest(dispatcher) {
        val repo = FakeSettingsRepository(AppSettings(endlessRadio = false)); OnboardingViewModel(repo).finish()
        assertTrue(repo.current.onboardingDone); assertFalse(repo.current.endlessRadio)
    }
}
