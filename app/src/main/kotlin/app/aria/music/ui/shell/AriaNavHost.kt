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

@Composable
fun AriaNavHost(nav: NavHostController, legacySearch: @Composable () -> Unit, modifier: Modifier = Modifier) {
    NavHost(nav, startDestination = HomeRoute, modifier = modifier) {
        composable<HomeRoute> { Placeholder(R.string.home_placeholder) }
        composable<SearchRoute> { legacySearch() }
        composable<NowPlayingRoute> { Placeholder(R.string.now_playing_placeholder) } // replaced by Task 14
        composable<LibraryRoute> { Placeholder(R.string.library_placeholder) }
    }
}

@Composable
private fun Placeholder(@StringRes text: Int) = EmptyState(title = text)
