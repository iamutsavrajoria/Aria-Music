// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.di

import app.aria.domain.BrowseSource
import app.aria.domain.SearchRepository
import app.aria.innertube.adapter.YouTubeBrowseSource
import app.aria.innertube.adapter.YouTubeSearchRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BrowseModule {
    @Provides @Singleton fun browse(): BrowseSource = YouTubeBrowseSource()
    @Provides @Singleton fun searchRepository(): SearchRepository = YouTubeSearchRepository()
}
