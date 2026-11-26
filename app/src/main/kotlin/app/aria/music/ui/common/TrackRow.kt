// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.aria.domain.Track
import app.aria.music.R
import app.aria.music.image.AriaImage
import app.aria.music.ui.Formatters

enum class TrackMenuAction { PLAY_NEXT, ADD_TO_QUEUE, LIKE, UNLIKE }

/**
 * One song row shared by Search, Home, Library and the detail pages: artwork, title, artists, duration and an overflow menu.
 * [liked] shows a heart and turns the menu entry into "Remove like" ([TrackMenuAction.UNLIKE]); the caller owns the state (library.isLiked).
 * [extraActions] are (string resource, callback) pairs appended to the menu (for example "Add to playlist").
 * [showDefaultMenu] = false leaves only [extraActions] in the menu, for a screen (Library) that owns the whole menu.
 * [disabledActions] lists [extraActions] labels shown greyed out; [menuTag] tags the overflow button for tests.
 */
@Composable
fun TrackRow(
    track: Track,
    onClick: () -> Unit,
    onMenu: (TrackMenuAction) -> Unit,
    modifier: Modifier = Modifier,
    liked: Boolean = false,
    extraActions: List<Pair<Int, () -> Unit>> = emptyList(),
    showDefaultMenu: Boolean = true,
    disabledActions: Set<Int> = emptySet(),
    menuTag: String? = null,
) {
    var menu by remember { mutableStateOf(false) }
    val artists = track.artists.joinToString(stringResource(R.string.artist_separator))
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AriaImage(track.thumbnailUrl, null, Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)))
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            Text(artists, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (liked) Icon(AriaIcons.Favorite, stringResource(R.string.track_liked), Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        track.durationMs?.let { Text(Formatters.duration(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        IconButton({ menu = true }, Modifier.size(48.dp).then(if (menuTag != null) Modifier.testTag(menuTag) else Modifier)) { Icon(AriaIcons.MoreVert, stringResource(R.string.track_more, track.title)) }
        DropdownMenu(menu, { menu = false }) {
            if (showDefaultMenu) {
                DropdownMenuItem({ Text(stringResource(R.string.track_play_next)) }, { menu = false; onMenu(TrackMenuAction.PLAY_NEXT) })
                DropdownMenuItem({ Text(stringResource(R.string.track_add_to_queue)) }, { menu = false; onMenu(TrackMenuAction.ADD_TO_QUEUE) })
                if (liked) DropdownMenuItem({ Text(stringResource(R.string.track_unlike)) }, { menu = false; onMenu(TrackMenuAction.UNLIKE) })
                else DropdownMenuItem({ Text(stringResource(R.string.track_like)) }, { menu = false; onMenu(TrackMenuAction.LIKE) })
            }
            extraActions.forEach { (label, action) -> DropdownMenuItem({ Text(stringResource(label)) }, { menu = false; action() }, enabled = label !in disabledActions) }
        }
    }
}
