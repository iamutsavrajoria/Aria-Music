// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.shell

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import app.aria.music.R
import app.aria.music.ui.common.EmptyState
import app.aria.music.ui.nowplaying.NowPlayingScreen
import app.aria.music.ui.queue.QueueScreen

@Composable
fun AriaNavHost(
    nav: NavHostController,
    legacySearch: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    /** Overridable so tests without a Hilt activity can swap out the Hilt-backed screen. */
    nowPlaying: @Composable (onBack: () -> Unit, onOpenQueue: () -> Unit) -> Unit = { back, queue -> NowPlayingScreen(back, queue) },
    queue: @Composable (onBack: () -> Unit) -> Unit = { back -> QueueScreen(back) },
) {
    NavHost(nav, startDestination = HomeRoute, modifier = modifier) {
        composable<HomeRoute> { Placeholder(R.string.home_placeholder) }
        composable<SearchRoute> { legacySearch() }
        composable<NowPlayingRoute> { nowPlaying({ nav.popBackStack() }, { nav.navigate(QueueRoute) { launchSingleTop = true } }) }
        composable<QueueRoute> { queue { nav.popBackStack() } }
        composable<LibraryRoute> { Placeholder(R.string.library_placeholder) }
    }
}

@Composable
private fun Placeholder(@StringRes text: Int) = EmptyState(title = text)
