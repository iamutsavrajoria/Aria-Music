// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.onboarding

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.aria.music.R
import app.aria.music.ui.shell.NotificationPermissionPolicy

const val TAG_ONBOARDING_NEXT = "onboarding:next"
const val TAG_ONBOARDING_BACK = "onboarding:back"
const val TAG_ONBOARDING_SKIP = "onboarding:skip"
const val TAG_ONBOARDING_FINISH = "onboarding:finish"
const val TAG_ONBOARDING_NOTIFY = "onboarding:notify"
const val TAG_ONBOARDING_PAGE = "onboarding:page"

enum class OnboardingPage { WELCOME, PRIVACY, NOTIFICATIONS }

data class OnboardingActions(val onFinish: () -> Unit, val onRequestNotifications: () -> Unit)

/** Welcome, Privacy and (only when [showNotificationPage]) Notifications. The permission is requested only by a tap on its button. */
@Composable
fun OnboardingContent(showNotificationPage: Boolean, actions: OnboardingActions, modifier: Modifier = Modifier) {
    val pages = remember(showNotificationPage) {
        listOfNotNull(OnboardingPage.WELCOME, OnboardingPage.PRIVACY, OnboardingPage.NOTIFICATIONS.takeIf { showNotificationPage })
    }
    var requested by rememberSaveable { mutableIntStateOf(0) }
    val index = requested.coerceIn(0, pages.lastIndex)
    val page = pages[index]
    val last = index == pages.lastIndex
    val counter = stringResource(R.string.onboarding_page_of, index + 1, pages.size)
    // System Back steps to the previous page; on the first page it leaves the app as usual.
    BackHandler(enabled = index > 0) { requested = index - 1 }
    Column(modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                counter,
                Modifier.weight(1f).testTag(TAG_ONBOARDING_PAGE).semantics { contentDescription = counter; liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!last) TextButton(actions.onFinish, Modifier.heightIn(min = 48.dp).testTag(TAG_ONBOARDING_SKIP)) { Text(stringResource(R.string.onboarding_skip)) }
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            val (title, body) = when (page) {
                OnboardingPage.WELCOME -> R.string.onboarding_welcome_title to stringResource(R.string.onboarding_welcome_body)
                OnboardingPage.PRIVACY -> R.string.onboarding_privacy_title to stringResource(R.string.onboarding_privacy_body, stringResource(R.string.app_name))
                OnboardingPage.NOTIFICATIONS -> R.string.onboarding_notifications_title to stringResource(R.string.onboarding_notifications_body)
            }
            Text(stringResource(title), Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
            Text(body, style = MaterialTheme.typography.bodyLarge)
            if (page == OnboardingPage.NOTIFICATIONS) {
                Button(actions.onRequestNotifications, Modifier.heightIn(min = 48.dp).testTag(TAG_ONBOARDING_NOTIFY)) { Text(stringResource(R.string.onboarding_notify_allow)) }
            }
        }
        // Back and the main button share the width and wrap their labels, so large fonts and long translations never push one off screen.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            if (index > 0) TextButton({ requested = index - 1 }, Modifier.weight(1f, fill = false).heightIn(min = 48.dp).testTag(TAG_ONBOARDING_BACK)) { Text(stringResource(R.string.onboarding_back)) }
            if (last) {
                Button(actions.onFinish, Modifier.weight(1f, fill = false).heightIn(min = 48.dp).testTag(TAG_ONBOARDING_FINISH)) { Text(stringResource(R.string.onboarding_finish)) }
            } else {
                Button({ requested = index + 1 }, Modifier.weight(1f, fill = false).heightIn(min = 48.dp).testTag(TAG_ONBOARDING_NEXT)) { Text(stringResource(R.string.onboarding_next)) }
            }
        }
    }
}

/** Runs [block] on the first call only, so a double tap on Get started or Skip navigates once. */
internal fun once(block: () -> Unit): () -> Unit {
    var done = false
    return { if (!done) { done = true; block() } }
}

@Composable
fun OnboardingScreen(onDone: () -> Unit, viewModel: OnboardingViewModel = hiltViewModel()) {
    val context = LocalContext.current
    // Decided once: granting on the Notifications page must not remove the page the person is looking at.
    val showNotificationPage = rememberSaveable {
        val granted = Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        NotificationPermissionPolicy.shouldExplain(Build.VERSION.SDK_INT, granted, alreadyAsked = false, hasQueue = true)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val currentDone by rememberUpdatedState(onDone)
    val finish = remember(viewModel) { once { viewModel.finish(); currentDone() } }
    val actions = remember(finish, launcher) {
        OnboardingActions(
            onFinish = finish,
            onRequestNotifications = { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) },
        )
    }
    OnboardingContent(showNotificationPage, actions)
}
