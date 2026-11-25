// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.library

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aria.domain.LibrarySong
import app.aria.domain.LibrarySort
import app.aria.domain.Track
import app.aria.music.R
import app.aria.music.ui.common.AriaIcons
import app.aria.music.ui.common.BrowseRow
import app.aria.music.ui.common.EmptyState
import app.aria.music.ui.common.ErrorState
import app.aria.music.ui.common.LoadingState
import app.aria.music.ui.common.TrackRow
import kotlinx.coroutines.delay

const val TAG_LIBRARY_LIST = "library:list"
const val TAG_LIBRARY_QUERY = "library:query"

private const val QUERY_DEBOUNCE_MS = 150L

data class LibraryActions(
    val onTab: (LibraryTab) -> Unit,
    val onQuery: (String) -> Unit,
    val onSort: (LibrarySort) -> Unit,
    val onPlaySong: (List<Track>, Int) -> Unit,
    val onPlayAll: () -> Unit,
    val onShuffleAll: () -> Unit,
    val onTrackMenu: (Track, LibraryTrackAction) -> Unit,
    val onOpenAlbum: (String) -> Unit,
    val onOpenArtist: (String) -> Unit,
    val onOpenPlaylist: (String) -> Unit,
)

@StringRes private fun LibraryTab.label() = when (this) {
    LibraryTab.SONGS -> R.string.library_tab_songs
    LibraryTab.ALBUMS -> R.string.library_tab_albums
    LibraryTab.ARTISTS -> R.string.library_tab_artists
    LibraryTab.PLAYLISTS -> R.string.library_tab_playlists
}

@StringRes private fun LibraryTab.emptyTitle() = when (this) {
    LibraryTab.SONGS -> R.string.library_empty_songs
    LibraryTab.ALBUMS -> R.string.library_empty_albums
    LibraryTab.ARTISTS -> R.string.library_empty_artists
    LibraryTab.PLAYLISTS -> R.string.library_empty_playlists
}

@Composable
fun LibraryContent(state: LibraryUiState, actions: LibraryActions, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = state.tab.ordinal) {
            LibraryTab.entries.forEach { t ->
                Tab(
                    selected = t == state.tab, onClick = { actions.onTab(t) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("library:tab:${t.name}"),
                    text = { Text(stringResource(t.label())) },
                )
            }
        }
        FilterField(state.query, actions.onQuery)
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(state.sort == LibrarySort.RECENTLY_ADDED, { actions.onSort(LibrarySort.RECENTLY_ADDED) }, { Text(stringResource(R.string.library_sort_recent)) })
            FilterChip(state.sort == LibrarySort.TITLE, { actions.onSort(LibrarySort.TITLE) }, { Text(stringResource(R.string.library_sort_title)) })
        }
        // A bounded slot: the states fill it, the lists scroll inside it.
        Column(Modifier.weight(1f).fillMaxWidth()) {
            when (val c = state.content) {
                LibraryContent.Loading -> LoadingState()
                LibraryContent.Empty -> EmptyState(state.tab.emptyTitle())
                LibraryContent.NoMatches -> EmptyState(R.string.library_no_matches)
                LibraryContent.Error -> ErrorState(onRetry = { actions.onTab(state.tab) })
                is LibraryContent.Songs -> SongList(c.rows, actions)
                is LibraryContent.Albums -> LazyColumn(Modifier.fillMaxSize().testTag(TAG_LIBRARY_LIST)) {
                    items(c.rows, key = { "album:" + it.id }) { a ->
                        BrowseRow(
                            a.thumbnailUrl, a.title, (a.artists + listOfNotNull(a.year?.toString())).joinToString(stringResource(R.string.artist_separator)),
                            round = false, R.string.browse_desc_album, Modifier.testTag("library:row:album:${a.id}"),
                        ) { actions.onOpenAlbum(a.id) }
                    }
                }
                is LibraryContent.Artists -> LazyColumn(Modifier.fillMaxSize().testTag(TAG_LIBRARY_LIST)) {
                    items(c.rows, key = { "artist:" + it.id }) { a ->
                        BrowseRow(a.thumbnailUrl, a.name, null, round = true, R.string.browse_desc_artist, Modifier.testTag("library:row:artist:${a.id}")) { actions.onOpenArtist(a.id) }
                    }
                }
                is LibraryContent.Playlists -> LazyColumn(Modifier.fillMaxSize().testTag(TAG_LIBRARY_LIST)) {
                    items(c.rows, key = { "playlist:" + it.id }) { p ->
                        val count = p.trackCount?.let { pluralStringResource(R.plurals.library_playlist_songs, it, it) }
                        BrowseRow(
                            p.thumbnailUrl, p.title, listOfNotNull(p.subtitle?.takeIf { it.isNotBlank() }, count).joinToString(stringResource(R.string.artist_separator)),
                            round = false, R.string.browse_desc_playlist, Modifier.testTag("library:row:playlist:${p.id}"),
                        ) { actions.onOpenPlaylist(p.id) }
                    }
                }
            }
        }
    }
}

/** The text is local so typing stays instant; the repository query follows 150 ms after the last keystroke. */
@Composable
private fun FilterField(query: String, onQuery: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(query) }
    LaunchedEffect(text) {
        if (text != query) { delay(QUERY_DEBOUNCE_MS); onQuery(text) }
    }
    OutlinedTextField(
        value = text, onValueChange = { text = it },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag(TAG_LIBRARY_QUERY),
        singleLine = true,
        placeholder = { Text(stringResource(R.string.library_filter_hint)) },
        trailingIcon = if (text.isEmpty()) null else {
            { IconButton({ text = "" }, Modifier.size(48.dp)) { Icon(AriaIcons.Close, stringResource(R.string.library_filter_clear)) } }
        },
    )
}

@Composable
private fun SongList(rows: List<LibrarySong>, actions: LibraryActions) {
    val tracks = remember(rows) { rows.map { it.track } }
    LazyColumn(Modifier.fillMaxSize().testTag(TAG_LIBRARY_LIST)) {
        item(key = "play-shuffle") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(actions.onPlayAll, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.library_play_all)) }
                OutlinedButton(actions.onShuffleAll, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.library_shuffle_all)) }
            }
        }
        itemsIndexed(rows, key = { _, r -> "song:" + r.track.id.raw }) { i, r ->
            val t = r.track
            TrackRow(
                t, onClick = { actions.onPlaySong(tracks, i) }, onMenu = { },
                modifier = Modifier.testTag("library:row:song:${t.id.raw}"),
                liked = r.liked, showDefaultMenu = false,
                extraActions = buildList {
                    add(R.string.track_play_next to { actions.onTrackMenu(t, LibraryTrackAction.PLAY_NEXT) })
                    add(R.string.track_add_to_queue to { actions.onTrackMenu(t, LibraryTrackAction.ADD_TO_QUEUE) })
                    if (r.liked) add(R.string.library_remove_like to { actions.onTrackMenu(t, LibraryTrackAction.UNLIKE) })
                    add(R.string.library_remove_from_library to { actions.onTrackMenu(t, LibraryTrackAction.REMOVE_FROM_LIBRARY) })
                },
            )
        }
    }
}

@Composable
fun LibraryScreen(
    onAlbum: (String) -> Unit,
    onArtist: (String) -> Unit,
    onPlaylist: (String) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    val actions = remember(viewModel, onAlbum, onArtist, onPlaylist) {
        LibraryActions(
            onTab = viewModel::setTab, onQuery = viewModel::setQuery, onSort = viewModel::setSort,
            onPlaySong = viewModel::play, onPlayAll = viewModel::playAll, onShuffleAll = viewModel::shuffleAll, onTrackMenu = viewModel::trackAction,
            onOpenAlbum = onAlbum, onOpenArtist = onArtist, onOpenPlaylist = onPlaylist,
        )
    }
    LibraryContent(state, actions)
}
