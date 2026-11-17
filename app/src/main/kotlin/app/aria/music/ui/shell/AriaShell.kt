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
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.aria.music.player.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@Composable
fun AriaShellContent(
    selected: TopLevel?,
    onSelect: (TopLevel) -> Unit,
    snackbarHost: SnackbarHostState,
    miniPlayer: @Composable () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        bottomBar = {
            Column {
                miniPlayer()
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

/** Holds the controller so the shell instantiates it at startup (it is a lazy singleton; early engine events must not be lost). */
@HiltViewModel
class ShellViewModel @Inject constructor(val controller: PlayerController) : ViewModel()

@Composable
fun AriaRoot(legacySearch: @Composable () -> Unit, controller: PlayerController = hiltViewModel<ShellViewModel>().controller) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalContext.current.resources
    val ui by controller.ui.collectAsStateWithLifecycle()
    LaunchedEffect(controller) {
        controller.messages.collect { message ->
            // A newer message replaces the one on screen; collection continues while it shows.
            snackbar.currentSnackbarData?.dismiss()
            launch { snackbar.showSnackbar(message.text(resources)) }
        }
    }
    NotificationPermissionGate(hasQueue = ui.hasQueue)
    val entry by nav.currentBackStackEntryAsState()
    val dest = entry?.destination
    val selected = TopLevel.entries.firstOrNull { t -> dest?.hierarchy?.any { it.hasRoute(t.route::class) } == true }
    AriaShellContent(
        selected = selected,
        onSelect = { t ->
            nav.navigate(t.route) {
                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        },
        snackbarHost = snackbar,
        miniPlayer = { /* Task 13 */ },
    ) { padding -> AriaNavHost(nav, legacySearch, Modifier.padding(padding)) }
}
