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
import app.aria.music.ui.search.SearchScreen

@Composable
fun AriaNavHost(
    nav: NavHostController,
    modifier: Modifier = Modifier,
    /** Overridable so tests without a Hilt activity can swap out the Hilt-backed screen. */
    nowPlaying: @Composable (onBack: () -> Unit, onOpenQueue: () -> Unit) -> Unit = { back, queue -> NowPlayingScreen(back, queue) },
    queue: @Composable (onBack: () -> Unit) -> Unit = { back -> QueueScreen(back) },
    search: @Composable (onAlbum: (String) -> Unit, onArtist: (String) -> Unit, onPlaylist: (String) -> Unit) -> Unit =
        { album, artist, playlist -> SearchScreen(album, artist, playlist) },
) {
    NavHost(nav, startDestination = HomeRoute, modifier = modifier) {
        composable<HomeRoute> { Placeholder(R.string.home_placeholder) }
        composable<SearchRoute> {
            search(
                { nav.navigate(AlbumRoute(it)) },
                { nav.navigate(ArtistRoute(it)) },
                { nav.navigate(PlaylistRoute(it, local = false)) },
            )
        }
        // Temporary destinations so the search callbacks have somewhere to go; Task 19 replaces them with the detail pages.
        composable<AlbumRoute> { Placeholder(R.string.coming_soon) }
        composable<ArtistRoute> { Placeholder(R.string.coming_soon) }
        composable<PlaylistRoute> { Placeholder(R.string.coming_soon) }
        composable<NowPlayingRoute> { nowPlaying({ nav.popBackStack() }, { nav.navigate(QueueRoute) { launchSingleTop = true } }) }
        composable<QueueRoute> { queue { nav.popBackStack() } }
        composable<LibraryRoute> { Placeholder(R.string.library_placeholder) }
    }
}

@Composable
private fun Placeholder(@StringRes text: Int) = EmptyState(title = text)
