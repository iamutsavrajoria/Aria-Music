// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.shell

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import app.aria.music.R
import app.aria.music.ui.common.AriaIcons
import kotlinx.serialization.Serializable

@Serializable data object HomeRoute
@Serializable data object SearchRoute
@Serializable data object LibraryRoute
@Serializable data object NowPlayingRoute
@Serializable data object QueueRoute
@Serializable data object SettingsRoute
@Serializable data object OnboardingRoute
@Serializable data class AlbumRoute(val id: String)
@Serializable data class ArtistRoute(val id: String)
@Serializable data class PlaylistRoute(val id: String, val local: Boolean)

enum class TopLevel(val route: Any, @StringRes val label: Int, val icon: ImageVector) {
    HOME(HomeRoute, R.string.nav_home, AriaIcons.Home),
    SEARCH(SearchRoute, R.string.nav_search, AriaIcons.Search),
    LIBRARY(LibraryRoute, R.string.nav_library, AriaIcons.LibraryMusic),
}
