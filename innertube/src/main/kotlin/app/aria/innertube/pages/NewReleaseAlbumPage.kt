// SPDX-License-Identifier: GPL-3.0-only
package app.aria.innertube.pages

import app.aria.innertube.models.AlbumItem
import app.aria.innertube.models.Artist
import app.aria.innertube.models.MusicTwoRowItemRenderer
import app.aria.innertube.models.oddElements
import app.aria.innertube.models.splitBySeparator

object NewReleaseAlbumPage {
    fun fromMusicTwoRowItemRenderer(renderer: MusicTwoRowItemRenderer): AlbumItem? {
        return AlbumItem(
            browseId = renderer.navigationEndpoint.browseEndpoint?.browseId ?: return null,
            playlistId =
                renderer.thumbnailOverlay
                    ?.musicItemThumbnailOverlayRenderer
                    ?.content
                    ?.musicPlayButtonRenderer
                    ?.playNavigationEndpoint
                    ?.watchPlaylistEndpoint
                    ?.playlistId ?: return null,
            title =
                renderer.title.runs
                    ?.firstOrNull()
                    ?.text ?: return null,
            artists =
                renderer.subtitle?.runs?.splitBySeparator()?.getOrNull(1)?.oddElements()?.map {
                    Artist(
                        name = it.text,
                        id = it.navigationEndpoint?.browseEndpoint?.browseId,
                    )
                } ?: return null,
            year =
                renderer.subtitle.runs
                    .lastOrNull()
                    ?.text
                    ?.toIntOrNull(),
            thumbnail = renderer.thumbnailRenderer.getThumbnailUrl() ?: return null,
            explicit =
                renderer.subtitleBadges?.find {
                    it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                } != null,
        )
    }
}
