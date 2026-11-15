// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.di

import android.content.Context
import app.aria.data.AriaDatabase
import app.aria.data.DataStoreSettingsRepository
import app.aria.data.HomeLocalSourceImpl
import app.aria.data.HomeShelfCacheImpl
import app.aria.data.LibraryRepositoryImpl
import app.aria.data.PlaylistRepositoryImpl
import app.aria.data.SearchHistoryRepositoryImpl
import app.aria.data.createSettingsDataStore
import app.aria.domain.BrowseSource
import app.aria.domain.DefaultHomeRepository
import app.aria.domain.HomeLocalSource
import app.aria.domain.HomeRepository
import app.aria.domain.HomeShelfCache
import app.aria.domain.LibraryRepository
import app.aria.domain.PlaylistRepository
import app.aria.domain.SearchHistoryRepository
import app.aria.domain.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides @Singleton fun library(db: AriaDatabase): LibraryRepository = LibraryRepositoryImpl(db.libraryDao())
    @Provides @Singleton fun playlists(db: AriaDatabase): PlaylistRepository = PlaylistRepositoryImpl(db.playlistDao())
    @Provides @Singleton fun homeLocal(db: AriaDatabase): HomeLocalSource = HomeLocalSourceImpl(db.homeLocalDao())
    @Provides @Singleton fun searchHistory(db: AriaDatabase): SearchHistoryRepository = SearchHistoryRepositoryImpl(db.searchHistoryDao())
    @Provides @Singleton fun homeCache(db: AriaDatabase): HomeShelfCache = HomeShelfCacheImpl(db.homeShelfCacheDao())

    @Provides @Singleton
    fun home(local: HomeLocalSource, browse: BrowseSource, cache: HomeShelfCache): HomeRepository = DefaultHomeRepository(local, browse, cache)

    /** One DataStore per file, app-lifetime scope, under `datastore/` (the only directory the backup rules exclude). */
    @Provides @Singleton
    fun settings(@ApplicationContext c: Context): SettingsRepository = DataStoreSettingsRepository(
        createSettingsDataStore(File(c.filesDir, "datastore/settings.preferences_pb"), CoroutineScope(SupervisorJob() + Dispatchers.IO)),
    )
}
