// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.shell

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import app.aria.music.R
import app.aria.music.ui.common.EmptyState
import app.aria.music.ui.detail.AlbumScreen
import app.aria.music.ui.detail.ArtistScreen
import app.aria.music.ui.detail.PlaylistScreen
import app.aria.music.ui.home.HomeScreen
import app.aria.music.ui.library.LibraryScreen
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
    home: @Composable (onAlbum: (String) -> Unit, onArtist: (String) -> Unit, onPlaylist: (String) -> Unit, onSettings: () -> Unit) -> Unit =
        { album, artist, playlist, settings -> HomeScreen(album, artist, playlist, settings) },
) {
    NavHost(nav, startDestination = HomeRoute, modifier = modifier) {
        composable<HomeRoute> {
            home(
                { nav.navigate(AlbumRoute(it)) },
                { nav.navigate(ArtistRoute(it)) },
                { nav.navigate(PlaylistRoute(it, local = false)) },
                { nav.navigate(SettingsRoute) { launchSingleTop = true } },
            )
        }
        composable<SearchRoute> {
            search(
                { nav.navigate(AlbumRoute(it)) },
                { nav.navigate(ArtistRoute(it)) },
                { nav.navigate(PlaylistRoute(it, local = false)) },
            )
        }
        composable<AlbumRoute> {
            AlbumScreen(onBack = { nav.popBackStack() }, onAlbum = { nav.navigate(AlbumRoute(it)) })
        }
        composable<ArtistRoute> {
            ArtistScreen(
                onBack = { nav.popBackStack() },
                onAlbum = { nav.navigate(AlbumRoute(it)) },
                onArtist = { nav.navigate(ArtistRoute(it)) },
                onPlaylist = { nav.navigate(PlaylistRoute(it, local = false)) },
            )
        }
        composable<PlaylistRoute> { entry ->
            // Temporary for local playlists until Task 20; the remote page is real.
            if (entry.toRoute<PlaylistRoute>().local) Placeholder(R.string.coming_soon) else PlaylistScreen(onBack = { nav.popBackStack() })
        }
        // Temporary until Task 21 builds the Settings screen.
        composable<SettingsRoute> { Placeholder(R.string.coming_soon) }
        composable<NowPlayingRoute> { nowPlaying({ nav.popBackStack() }, { nav.navigate(QueueRoute) { launchSingleTop = true } }) }
        composable<QueueRoute> { queue { nav.popBackStack() } }
        composable<LibraryRoute> {
            LibraryScreen(
                onAlbum = { nav.navigate(AlbumRoute(it)) },
                onArtist = { nav.navigate(ArtistRoute(it)) },
                onPlaylist = { nav.navigate(PlaylistRoute(it, local = true)) },
            )
        }
    }
}

@Composable
private fun Placeholder(@StringRes text: Int) = EmptyState(title = text)
