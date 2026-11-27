// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.aria.music.player.PlayerController
import app.aria.music.player.PlayerMessage
import app.aria.music.ui.miniplayer.MiniPlayerHost
import app.aria.music.ui.nowplaying.NowPlayingScreen
import app.aria.music.ui.onboarding.OnboardingScreen
import app.aria.music.ui.queue.QueueScreen
import app.aria.music.ui.home.HomeScreen
import app.aria.music.ui.search.SearchScreen
import app.aria.music.ui.settings.SettingsScreen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

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

/**
 * Switches tabs keeping each tab's back stack and state (select Library, drill in, go Home, come back: Library is as it was).
 * Pops to Home, not to the graph's start: on the first run the start is Onboarding, which is gone from the back stack once finished.
 */
fun NavController.navigateTopLevel(t: TopLevel) {
    navigate(t.route) {
        popUpTo<HomeRoute> { saveState = true }
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

private val FULL_SCREEN_ROUTES = listOf(NowPlayingRoute::class, QueueRoute::class, SettingsRoute::class, OnboardingRoute::class)

@Composable
fun AriaRoot(
    controller: PlayerController = hiltViewModel<ShellViewModel>().controller,
    /** Null while the stored flag is still being read; overridable so tests without a Hilt activity can pick the start destination. */
    onboardingDone: Boolean? = hiltViewModel<ShellViewModel>().onboardingDone.collectAsStateWithLifecycle().value,
    /** Overridable so tests without a Hilt activity can swap out the Hilt-backed mini player. */
    miniPlayer: @Composable (onExpand: () -> Unit) -> Unit = { MiniPlayerHost(onExpand = it) },
    nowPlaying: @Composable (onBack: () -> Unit, onOpenQueue: () -> Unit) -> Unit = { back, queue -> NowPlayingScreen(back, queue) },
    queue: @Composable (onBack: () -> Unit) -> Unit = { back -> QueueScreen(back) },
    search: @Composable (onAlbum: (String) -> Unit, onArtist: (String) -> Unit, onPlaylist: (String) -> Unit) -> Unit =
        { album, artist, playlist -> SearchScreen(album, artist, playlist) },
    home: @Composable (onAlbum: (String) -> Unit, onArtist: (String) -> Unit, onPlaylist: (String) -> Unit, onSettings: () -> Unit) -> Unit =
        { album, artist, playlist, settings -> HomeScreen(album, artist, playlist, settings) },
    settings: @Composable (onBack: () -> Unit) -> Unit = { back -> SettingsScreen(back) },
    onboarding: @Composable (onDone: () -> Unit) -> Unit = { done -> OnboardingScreen(done) },
) {
    // Nothing but an empty surface until the flag is known, so a returning user never sees onboarding (or a first-run user Home) flash by.
    if (onboardingDone == null) { Box(Modifier.fillMaxSize()); return }
    // Captured once: the flag flips to true when onboarding finishes, and that must not swap the start destination (it would reset the back stack).
    val start: Any = remember { if (onboardingDone) HomeRoute else OnboardingRoute }
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
    ) { padding -> AriaNavHost(nav, start, Modifier.padding(padding), nowPlaying, queue, search, home, settings, onboarding) }
}
