// SPDX-License-Identifier: GPL-3.0-only
package app.aria.innertube.adapter

import app.aria.domain.Track
import app.aria.domain.TrackId
import app.aria.innertube.models.SongItem

fun SongItem.toTrack(): Track = Track(
    id = TrackId.youtube(id),
    title = title,
    artists = artists.map { it.name },
    durationMs = duration?.let { it * 1000L },
    thumbnailUrl = thumbnail,
)
