// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import app.aria.music.ui.detail.AlbumScreen
import app.aria.music.ui.detail.ArtistScreen
import app.aria.music.ui.detail.PlaylistScreen
import app.aria.music.ui.home.HomeScreen
import app.aria.music.ui.library.LibraryScreen
import app.aria.music.ui.onboarding.OnboardingScreen
import app.aria.music.ui.playlists.LocalPlaylistScreen
import app.aria.music.ui.nowplaying.NowPlayingScreen
import app.aria.music.ui.queue.QueueScreen
import app.aria.music.ui.search.SearchScreen
import app.aria.music.ui.settings.SettingsScreen

@Composable
fun AriaNavHost(
    nav: NavHostController,
    start: Any,
    modifier: Modifier = Modifier,
    /** Overridable so tests without a Hilt activity can swap out the Hilt-backed screen. */
    nowPlaying: @Composable (onBack: () -> Unit, onOpenQueue: () -> Unit) -> Unit = { back, queue -> NowPlayingScreen(back, queue) },
    queue: @Composable (onBack: () -> Unit) -> Unit = { back -> QueueScreen(back) },
    search: @Composable (onAlbum: (String) -> Unit, onArtist: (String) -> Unit, onPlaylist: (String) -> Unit) -> Unit =
        { album, artist, playlist -> SearchScreen(album, artist, playlist) },
    home: @Composable (onAlbum: (String) -> Unit, onArtist: (String) -> Unit, onPlaylist: (String) -> Unit, onSettings: () -> Unit) -> Unit =
        { album, artist, playlist, settings -> HomeScreen(album, artist, playlist, settings) },
    settings: @Composable (onBack: () -> Unit) -> Unit = { back -> SettingsScreen(back) },
    onboarding: @Composable (onDone: () -> Unit) -> Unit = { done -> OnboardingScreen(done) },
) {
    NavHost(nav, startDestination = start, modifier = modifier) {
        composable<OnboardingRoute> { onboarding { nav.navigate(HomeRoute) { popUpTo<OnboardingRoute> { inclusive = true } } } }
        composable<HomeRoute> {
            home(
                { nav.navigate(AlbumRoute(it)) { launchSingleTop = true } },
                { nav.navigate(ArtistRoute(it)) { launchSingleTop = true } },
                { nav.navigate(PlaylistRoute(it, local = false)) { launchSingleTop = true } },
                { nav.navigate(SettingsRoute) { launchSingleTop = true } },
            )
        }
        composable<SearchRoute> {
            search(
                { nav.navigate(AlbumRoute(it)) { launchSingleTop = true } },
                { nav.navigate(ArtistRoute(it)) { launchSingleTop = true } },
                { nav.navigate(PlaylistRoute(it, local = false)) { launchSingleTop = true } },
            )
        }
        composable<AlbumRoute> {
            AlbumScreen(onBack = { nav.popBackStack() }, onAlbum = { nav.navigate(AlbumRoute(it)) { launchSingleTop = true } })
        }
        composable<ArtistRoute> {
            ArtistScreen(
                onBack = { nav.popBackStack() },
                onAlbum = { nav.navigate(AlbumRoute(it)) { launchSingleTop = true } },
                onArtist = { nav.navigate(ArtistRoute(it)) { launchSingleTop = true } },
                onPlaylist = { nav.navigate(PlaylistRoute(it, local = false)) { launchSingleTop = true } },
            )
        }
        composable<PlaylistRoute> { entry ->
            if (entry.toRoute<PlaylistRoute>().local) LocalPlaylistScreen(onBack = { nav.popBackStack() }) else PlaylistScreen(onBack = { nav.popBackStack() })
        }
        composable<SettingsRoute> { settings { nav.popBackStack() } }
        composable<NowPlayingRoute> { nowPlaying({ nav.popBackStack() }, { nav.navigate(QueueRoute) { launchSingleTop = true } }) }
        composable<QueueRoute> { queue { nav.popBackStack() } }
        composable<LibraryRoute> {
            LibraryScreen(
                onAlbum = { nav.navigate(AlbumRoute(it)) { launchSingleTop = true } },
                onArtist = { nav.navigate(ArtistRoute(it)) { launchSingleTop = true } },
                onPlaylist = { nav.navigate(PlaylistRoute(it, local = true)) { launchSingleTop = true } },
            )
        }
    }
}
