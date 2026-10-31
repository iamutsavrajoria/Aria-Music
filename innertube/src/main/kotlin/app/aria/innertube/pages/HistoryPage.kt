// SPDX-License-Identifier: GPL-3.0-only
package app.aria.innertube.pages

import app.aria.innertube.models.Album
import app.aria.innertube.models.Artist
import app.aria.innertube.models.MusicResponsiveListItemRenderer
import app.aria.innertube.models.MusicShelfRenderer
import app.aria.innertube.models.SongItem
import app.aria.innertube.models.getItems
import app.aria.innertube.models.oddElements
import app.aria.innertube.models.splitBySeparator
import app.aria.innertube.utils.parseTime

data class HistoryPage(
    val sections: List<HistorySection>?,
) {
    data class HistorySection(
        val title: String,
        val songs: List<SongItem>
    )

    companion object {
        fun fromMusicShelfRenderer(renderer: MusicShelfRenderer): HistorySection {
            return HistorySection(
                title = renderer.title?.runs?.firstOrNull()?.text!!,
                songs = renderer.contents?.getItems()?.mapNotNull {
                    fromMusicResponsiveListItemRenderer(it)
                }!!
            )
        }

        private fun fromMusicResponsiveListItemRenderer(renderer: MusicResponsiveListItemRenderer): SongItem? {
            // Extract library tokens using the new method that properly handles multiple toggle items
            val libraryTokens = PageHelper.extractLibraryTokensFromMenuItems(renderer.menu?.menuRenderer?.items)

            // Split the secondary line by bullet separator to separate artists from other metadata (like views)
            val secondaryLineRuns = renderer.flexColumns
                .getOrNull(1)
                ?.musicResponsiveListItemFlexColumnRenderer
                ?.text
                ?.runs
                ?.splitBySeparator()

            return SongItem(
                id = renderer.videoId ?: return null,
                title = renderer.flexColumns.firstOrNull()
                    ?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()
                    ?.text ?: return null,
                artists = secondaryLineRuns?.firstOrNull()?.oddElements()?.map {
                    Artist(
                        name = it.text,
                        id = it.navigationEndpoint?.browseEndpoint?.browseId
                    )
                } ?: emptyList(),
                album = renderer.flexColumns.getOrNull(3)?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()?.let {
                    Album(
                        name = it.text,
                        id = it.navigationEndpoint?.browseEndpoint?.browseId ?: return@let null
                    )
                },
                duration = renderer.fixedColumns?.firstOrNull()?.musicResponsiveListItemFlexColumnRenderer
                    ?.text?.runs?.firstOrNull()?.text?.parseTime(),
                musicVideoType = renderer.musicVideoType,
                thumbnail = renderer.thumbnail?.getThumbnailUrl() ?: return null,
                explicit = renderer.badges?.find {
                    it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                } != null,
                endpoint = renderer.overlay?.musicItemThumbnailOverlayRenderer?.content
                    ?.musicPlayButtonRenderer?.playNavigationEndpoint?.watchEndpoint,
                libraryAddToken = libraryTokens.addToken,
                libraryRemoveToken = libraryTokens.removeToken,
                historyRemoveToken = renderer.menu?.menuRenderer?.items?.find {
                    it.menuServiceItemRenderer?.icon?.iconType == "REMOVE_FROM_HISTORY"
                }?.menuServiceItemRenderer?.serviceEndpoint?.feedbackEndpoint?.feedbackToken,
                isEpisode = renderer.isEpisode
            )
        }
    }
}
