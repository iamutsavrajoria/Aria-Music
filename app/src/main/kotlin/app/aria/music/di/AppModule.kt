// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.di

import android.content.Context
import androidx.room.Room
import app.aria.data.AriaDatabase
import app.aria.data.PlayEventRepositoryImpl
import app.aria.domain.PlayEventRepository
import app.aria.domain.SearchSource
import app.aria.domain.StreamSource
import app.aria.innertube.adapter.YouTubeSearchSource
import app.aria.innertube.adapter.YouTubeStreamSource
import app.aria.music.SharedPrefsResumeStore
import app.aria.playback.ResumeStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun database(@ApplicationContext c: Context): AriaDatabase =
        Room.databaseBuilder(c, AriaDatabase::class.java, "aria.db").build() // never fall back to a destructive migration

    @Provides @Singleton fun playEvents(db: AriaDatabase): PlayEventRepository = PlayEventRepositoryImpl(db.playEventDao())
    @Provides @Singleton fun search(): SearchSource = YouTubeSearchSource()
    @Provides @Singleton fun streams(): StreamSource = YouTubeStreamSource()
    @Provides @Singleton fun resume(@ApplicationContext c: Context): ResumeStore = SharedPrefsResumeStore(c)
}
