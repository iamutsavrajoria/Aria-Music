// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.common

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.aria.music.image.AriaImage

/** One album, artist or playlist row shared by Search and Library; the merged description names the type for TalkBack. */
@Composable
fun BrowseRow(thumbnailUrl: String?, title: String, subtitle: String?, round: Boolean, @StringRes typeDescription: Int, modifier: Modifier, onClick: () -> Unit) {
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
