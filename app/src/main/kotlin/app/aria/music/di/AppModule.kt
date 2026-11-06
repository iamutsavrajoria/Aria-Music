// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.di

import android.content.Context
import androidx.room.Room
import app.aria.data.AriaDatabase
import app.aria.data.PlayEventRepositoryImpl
import app.aria.data.QueueStoreImpl
import app.aria.domain.NetworkMonitor
import app.aria.domain.PlayEventRepository
import app.aria.domain.PlaybackEngine
import app.aria.domain.QueueStore
import app.aria.domain.RadioSource
import app.aria.domain.SearchSource
import app.aria.domain.StreamSource
import app.aria.innertube.adapter.YouTubeRadioSource
import app.aria.innertube.adapter.YouTubeSearchSource
import app.aria.innertube.adapter.YouTubeStreamSource
import app.aria.innertube.extraction.ExtractorSession
import app.aria.innertube.extraction.PoTokenSource
import app.aria.innertube.potoken.WebViewPoTokenSource
import app.aria.music.ConnectivityNetworkMonitor
import app.aria.playback.DefaultPlaybackEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun database(@ApplicationContext c: Context): AriaDatabase =
        Room.databaseBuilder(c, AriaDatabase::class.java, "aria.db").build() // never fall back to a destructive migration

    @Provides @Singleton fun playEvents(db: AriaDatabase): PlayEventRepository = PlayEventRepositoryImpl(db.playEventDao())
    @Provides @Singleton fun search(): SearchSource = YouTubeSearchSource()
    @Provides @Singleton fun network(@ApplicationContext c: Context): NetworkMonitor = ConnectivityNetworkMonitor(c)
    @Provides @Singleton fun queueStore(db: AriaDatabase): QueueStore = QueueStoreImpl(db.queueDao())

    @Provides @Singleton fun radio(): RadioSource = YouTubeRadioSource()
    @Provides @Singleton fun poTokens(@ApplicationContext c: Context): PoTokenSource = WebViewPoTokenSource(c)
    @Provides @Singleton fun extractorSession(p: PoTokenSource): ExtractorSession = ExtractorSession.createDefault(p)
    @Provides @Singleton fun streams(s: ExtractorSession): StreamSource = YouTubeStreamSource.create(s)

    @Provides @Singleton
    fun engine(
        streams: StreamSource, store: QueueStore, radio: RadioSource, history: PlayEventRepository, network: NetworkMonitor,
    ): DefaultPlaybackEngine = DefaultPlaybackEngine(streams, store, radio, history, network, Dispatchers.Main.immediate)

    @Provides @Singleton fun playbackEngine(e: DefaultPlaybackEngine): PlaybackEngine = e
}
