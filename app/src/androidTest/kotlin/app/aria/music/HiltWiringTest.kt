// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music

import app.aria.domain.BrowseSource
import app.aria.domain.HomeRepository
import app.aria.domain.LibraryRepository
import app.aria.domain.PlaybackEngine
import app.aria.domain.PlaylistRepository
import app.aria.domain.SearchRepository
import app.aria.domain.SettingsRepository
import app.aria.domain.StreamSource
import app.aria.music.player.PlayerController
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class HiltWiringTest {
    @get:Rule val hilt = HiltAndroidRule(this)
    @Inject lateinit var engine: PlaybackEngine
    @Inject lateinit var streams: StreamSource

    @Inject lateinit var library: LibraryRepository
    @Inject lateinit var playlists: PlaylistRepository
    @Inject lateinit var home: HomeRepository
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var browse: BrowseSource
    @Inject lateinit var searchRepository: SearchRepository
    @Inject lateinit var controller: PlayerController

    @Test fun graphResolves() {
        hilt.inject()
        listOf(engine, streams, library, playlists, home, settings, browse, searchRepository, controller).forEach { assertNotNull(it) }
    }
}
