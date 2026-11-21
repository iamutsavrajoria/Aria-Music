// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.shell

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.aria.music.player.PlayerController
import app.aria.music.player.PlayerMessage
import app.aria.music.ui.miniplayer.MiniPlayerHost
import app.aria.music.ui.nowplaying.NowPlayingScreen
import app.aria.music.ui.queue.QueueScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import javax.inject.Inject

@Composable
fun AriaShellContent(
    selected: TopLevel?,
    onSelect: (TopLevel) -> Unit,
    snackbarHost: SnackbarHostState,
    miniPlayer: @Composable () -> Unit,
    miniPlayerVisible: Boolean = true,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        bottomBar = {
            Column {
                if (miniPlayerVisible) miniPlayer()
                if (selected != null) {
                    NavigationBar {
                        TopLevel.entries.forEach { t ->
                            NavigationBarItem(
                                selected = t == selected,
                                onClick = { onSelect(t) },
                                icon = { Icon(t.icon, contentDescription = null) },
                                label = { Text(stringResource(t.label)) },
                                modifier = Modifier.testTag("tab:${t.name}").defaultMinSize(minHeight = 48.dp),
                            )
                        }
                    }
                }
            }
        },
        content = content,
    )
}

/** Switches tabs keeping each tab's back stack and state (select Library, drill in, go Home, come back: Library is as it was). */
fun NavController.navigateTopLevel(t: TopLevel) {
    navigate(t.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * Shows each player message as a snackbar. collectLatest cancels the snackbar on screen (which dismisses it) as soon as a newer message
 * arrives, so the newest message always wins and an older one that has not been shown yet can never appear after it.
 */
@Composable
fun PlayerMessageHost(messages: Flow<PlayerMessage>, snackbar: SnackbarHostState) {
    val resources = LocalContext.current.resources
    LaunchedEffect(messages) {
        messages.collectLatest { message -> snackbar.showSnackbar(message.text(resources)) }
    }
}

/** Holds the controller so the shell instantiates it at startup (it is a lazy singleton; early engine events must not be lost). */
@HiltViewModel
class ShellViewModel @Inject constructor(val controller: PlayerController) : ViewModel()

private val FULL_SCREEN_ROUTES = listOf(NowPlayingRoute::class, QueueRoute::class, SettingsRoute::class, OnboardingRoute::class)

@Composable
fun AriaRoot(
    legacySearch: @Composable () -> Unit,
    controller: PlayerController = hiltViewModel<ShellViewModel>().controller,
    /** Overridable so tests without a Hilt activity can swap out the Hilt-backed mini player. */
    miniPlayer: @Composable (onExpand: () -> Unit) -> Unit = { MiniPlayerHost(onExpand = it) },
    nowPlaying: @Composable (onBack: () -> Unit, onOpenQueue: () -> Unit) -> Unit = { back, queue -> NowPlayingScreen(back, queue) },
    queue: @Composable (onBack: () -> Unit) -> Unit = { back -> QueueScreen(back) },
) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val ui by controller.ui.collectAsStateWithLifecycle()
    PlayerMessageHost(controller.messages, snackbar)
    NotificationPermissionGate(hasQueue = ui.hasQueue)
    val entry by nav.currentBackStackEntryAsState()
    val dest = entry?.destination
    val selected = TopLevel.entries.firstOrNull { t -> dest?.hierarchy?.any { it.hasRoute(t.route::class) } == true }
    // Full-screen routes own the whole screen: no bottom bar and no mini player.
    val fullScreen = dest?.let { d -> FULL_SCREEN_ROUTES.any { d.hasRoute(it) } } == true
    AriaShellContent(
        selected = selected,
        onSelect = nav::navigateTopLevel,
        snackbarHost = snackbar,
        miniPlayer = { miniPlayer { nav.navigate(NowPlayingRoute) { launchSingleTop = true } } },
        miniPlayerVisible = !fullScreen,
    ) { padding -> AriaNavHost(nav, legacySearch, Modifier.padding(padding), nowPlaying, queue) }
}
