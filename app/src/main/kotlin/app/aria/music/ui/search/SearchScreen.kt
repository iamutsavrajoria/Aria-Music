// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.search

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aria.domain.AlbumRef
import app.aria.domain.ArtistRef
import app.aria.domain.PlaylistRef
import app.aria.domain.SearchResults
import app.aria.domain.Track
import app.aria.music.R
import app.aria.music.image.AriaImage
import app.aria.music.ui.common.AriaIcons
import app.aria.music.ui.common.EmptyState
import app.aria.music.ui.common.ErrorState
import app.aria.music.ui.common.LoadingState
import app.aria.music.ui.common.OfflineState
import app.aria.music.ui.common.TrackMenuAction
import app.aria.music.ui.common.TrackRow

data class SearchActions(
    val onQueryChange: (String) -> Unit,
    val onSubmit: (String) -> Unit,
    val onRetry: () -> Unit,
    val onRemoveHistory: (String) -> Unit,
    val onClearHistory: () -> Unit,
    val onPlaySong: (List<Track>, Int) -> Unit,
    val onTrackMenu: (Track, TrackMenuAction) -> Unit,
    val onOpenAlbum: (String) -> Unit,
    val onOpenArtist: (String) -> Unit,
    val onOpenPlaylist: (String) -> Unit,
)

/** One line of the grouped results list. [key] is unique within the list even when the source ids repeat. */
internal sealed class ResultRow(val key: String) {
    class Header(key: String, @StringRes val title: Int) : ResultRow(key)
    class Song(key: String, val track: Track, val index: Int) : ResultRow(key)
    class Album(key: String, val album: AlbumRef) : ResultRow(key)
    class Artist(key: String, val artist: ArtistRef) : ResultRow(key)
    class Playlist(key: String, val playlist: PlaylistRef) : ResultRow(key)
}

/** Sections in a fixed order (Songs, Albums, Artists, Playlists); an empty section has no header. */
internal fun resultRows(r: SearchResults): List<ResultRow> {
    val seen = HashMap<String, Int>()
    fun unique(base: String): String { val n = seen.merge(base, 1, Int::plus)!!; return if (n == 1) base else "$base#$n" }
    return buildList {
        if (r.songs.isNotEmpty()) {
            add(ResultRow.Header("header:songs", R.string.search_section_songs))
            r.songs.forEachIndexed { i, t -> add(ResultRow.Song(unique("song:${t.id.raw}"), t, i)) }
        }
        if (r.albums.isNotEmpty()) {
            add(ResultRow.Header("header:albums", R.string.search_section_albums))
            r.albums.forEach { add(ResultRow.Album(unique("album:${it.id}"), it)) }
        }
        if (r.artists.isNotEmpty()) {
            add(ResultRow.Header("header:artists", R.string.search_section_artists))
            r.artists.forEach { add(ResultRow.Artist(unique("artist:${it.id}"), it)) }
        }
        if (r.playlists.isNotEmpty()) {
            add(ResultRow.Header("header:playlists", R.string.search_section_playlists))
            r.playlists.forEach { add(ResultRow.Playlist(unique("playlist:${it.id}"), it)) }
        }
    }
}

@Composable
fun SearchContent(state: SearchUiState, actions: SearchActions, modifier: Modifier = Modifier) {
    val focus = LocalFocusManager.current
    Column(modifier.fillMaxSize()) {
        SearchField(state.query, actions, focus)
        when (val mode = state.mode) {
            is SearchMode.Idle -> SuggestionList(emptyList(), mode.recent, actions)
            is SearchMode.Suggestions -> SuggestionList(mode.suggestions, mode.recent, actions)
            SearchMode.Loading -> LoadingState()
            is SearchMode.Results -> ResultsList(mode.results, actions)
            SearchMode.Empty -> EmptyState(R.string.search_empty)
            SearchMode.Error -> ErrorState(actions.onRetry)
            SearchMode.Offline -> OfflineState(actions.onRetry)
        }
    }
}

@Composable
private fun SearchField(query: String, actions: SearchActions, focus: FocusManager) {
    OutlinedTextField(
        value = query,
        onValueChange = actions.onQueryChange,
        modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("search:field"),
        singleLine = true,
        placeholder = { Text(stringResource(R.string.search_hint)) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus(); actions.onSubmit(query) }),
        trailingIcon = if (query.isEmpty()) null else {
            { IconButton({ actions.onQueryChange("") }, Modifier.size(48.dp)) { Icon(AriaIcons.Close, stringResource(R.string.search_clear_query)) } }
        },
    )
}

@Composable
private fun SuggestionList(suggestions: List<String>, recent: List<String>, actions: SearchActions) {
    val shownSuggestions = remember(suggestions) { suggestions.distinct() }
    val shownRecent = remember(shownSuggestions, recent) { recent.filter { it !in shownSuggestions } }
    LazyColumn(Modifier.fillMaxWidth().testTag("search:suggestions")) {
        items(shownSuggestions, key = { "suggestion:$it" }) { text ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { actions.onSubmit(text) }.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(AriaIcons.Search, null, Modifier.size(20.dp))
                Text(text, Modifier.weight(1f).padding(start = 16.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (shownRecent.isNotEmpty()) {
            item(key = "recent:header") {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.search_recent), Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleSmall)
                    TextButton(actions.onClearHistory, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.search_clear_history)) }
                }
            }
            items(shownRecent, key = { "recent:$it" }) { text ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { actions.onSubmit(text) }.padding(start = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    IconButton({ actions.onRemoveHistory(text) }, Modifier.size(48.dp)) { Icon(AriaIcons.Close, stringResource(R.string.search_remove_history, text)) }
                }
            }
        }
    }
}

@Composable
private fun ResultsList(results: SearchResults, actions: SearchActions) {
    val rows = remember(results) { resultRows(results) }
    LazyColumn(Modifier.fillMaxSize().testTag("search:results")) {
        items(rows, key = { it.key }) { row ->
            when (row) {
                is ResultRow.Header -> Text(
                    stringResource(row.title),
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp).semantics { heading() },
                    style = MaterialTheme.typography.titleMedium,
                )
                is ResultRow.Song -> TrackRow(row.track, { actions.onPlaySong(results.songs, row.index) }, { actions.onTrackMenu(row.track, it) })
                is ResultRow.Album -> BrowseRow(
                    row.album.thumbnailUrl, row.album.title,
                    (row.album.artists + listOfNotNull(row.album.year?.toString())).joinToString(stringResource(R.string.artist_separator)),
                    round = false, R.string.browse_desc_album, Modifier.testTag("search:album:${row.album.id}"),
                ) { actions.onOpenAlbum(row.album.id) }
                is ResultRow.Artist -> BrowseRow(row.artist.thumbnailUrl, row.artist.name, null, round = true, R.string.browse_desc_artist, Modifier.testTag("search:artist:${row.artist.id}")) { actions.onOpenArtist(row.artist.id) }
                is ResultRow.Playlist -> BrowseRow(row.playlist.thumbnailUrl, row.playlist.title, row.playlist.subtitle, round = false, R.string.browse_desc_playlist, Modifier.testTag("search:playlist:${row.playlist.id}")) { actions.onOpenPlaylist(row.playlist.id) }
            }
        }
    }
}

@Composable
private fun BrowseRow(thumbnailUrl: String?, title: String, subtitle: String?, round: Boolean, @StringRes typeDescription: Int, modifier: Modifier, onClick: () -> Unit) {
    val description = stringResource(typeDescription, title)
    Row(
        modifier.semantics(mergeDescendants = true) { contentDescription = description }.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AriaImage(thumbnailUrl, null, Modifier.size(40.dp).clip(RoundedCornerShape(if (round) 20.dp else 6.dp)))
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            if (!subtitle.isNullOrBlank()) Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SearchScreen(
    onAlbum: (String) -> Unit,
    onArtist: (String) -> Unit,
    onPlaylist: (String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    val actions = remember(viewModel, onAlbum, onArtist, onPlaylist) {
        SearchActions(
            onQueryChange = viewModel::onQueryChange, onSubmit = { viewModel.submit(it) }, onRetry = viewModel::retry,
            onRemoveHistory = viewModel::removeHistory, onClearHistory = viewModel::clearHistory,
            onPlaySong = viewModel::playSong,
            onTrackMenu = { track, action ->
                when (action) {
                    TrackMenuAction.PLAY_NEXT -> viewModel.playNext(track)
                    TrackMenuAction.ADD_TO_QUEUE -> viewModel.addToQueue(track)
                    TrackMenuAction.LIKE -> viewModel.toggleLike(track, true)
                    TrackMenuAction.UNLIKE -> viewModel.toggleLike(track, false)
                }
            },
            onOpenAlbum = onAlbum, onOpenArtist = onArtist, onOpenPlaylist = onPlaylist,
        )
    }
    SearchContent(state, actions)
}
