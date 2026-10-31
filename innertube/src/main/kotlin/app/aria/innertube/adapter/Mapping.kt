// SPDX-License-Identifier: GPL-3.0-only
package app.aria.innertube.adapter

import app.aria.domain.Track
import app.aria.domain.TrackId
import app.aria.innertube.models.SongItem
import app.aria.innertube.models.response.PlayerResponse.StreamingData

fun SongItem.toTrack(): Track = Track(
    id = TrackId.youtube(id),
    title = title,
    artists = artists.map { it.name },
    durationMs = duration?.let { it * 1000L },
    thumbnailUrl = thumbnail,
)

/** Highest-bitrate audio-only format that already carries a direct URL. */
fun StreamingData.bestAudio(): StreamingData.Format? =
    adaptiveFormats.filter { it.isAudio && it.url != null }.maxByOrNull { it.bitrate }
