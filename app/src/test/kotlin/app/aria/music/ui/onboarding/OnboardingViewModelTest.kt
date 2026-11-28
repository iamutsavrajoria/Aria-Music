// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import app.aria.domain.AppSettings
import app.aria.domain.SettingsRepository
import app.aria.domain.testing.FakeSettingsRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    @Before fun setMain() = Dispatchers.setMain(dispatcher)
    @After fun resetMain() = Dispatchers.resetMain()

    @Test fun aFailingWriteIsSwallowed() = runTest(dispatcher) {
        val repo = object : SettingsRepository by FakeSettingsRepository() { override suspend fun update(transform: (AppSettings) -> AppSettings) { error("disk") } }
        OnboardingViewModel(repo).finish()   // must not throw into the scope
    }

    @Test fun theWriteCompletesEvenWhenTheViewModelIsClearedBeforeItFinishes() = runTest(dispatcher) {
        val base = FakeSettingsRepository(); val gate = CompletableDeferred<Unit>()
        val repo = object : SettingsRepository by base { override suspend fun update(transform: (AppSettings) -> AppSettings) { gate.await(); base.update(transform) } }
        val store = ViewModelStore()
        val vm = ViewModelProvider(store, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = OnboardingViewModel(repo) as T
        })[OnboardingViewModel::class.java]
        vm.finish()
        assertFalse(base.current.onboardingDone)
        store.clear()                  // the onboarding entry was popped: viewModelScope is cancelled
        gate.complete(Unit)
        assertTrue(base.current.onboardingDone)
    }

    @Test fun finishKeepsOtherSettings() = runTest(dispatcher) {
        val repo = FakeSettingsRepository(AppSettings(endlessRadio = false)); OnboardingViewModel(repo).finish()
        assertTrue(repo.current.onboardingDone); assertEquals(false, repo.current.endlessRadio)
    }
}

class OnceTest {
    @Test fun runsTheBlockOnlyOnTheFirstCall() {
        var n = 0; val f = once { n++ }
        f(); f(); f()
        assertEquals(1, n)
    }
}
