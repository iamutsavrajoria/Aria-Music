// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.di

import android.content.Context
import app.aria.music.player.ServiceLauncher
import app.aria.playback.AriaPlaybackService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object UiModule {
    @Provides @Singleton @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Provides @Singleton
    fun serviceLauncher(@ApplicationContext c: Context): ServiceLauncher = ServiceLauncher { AriaPlaybackService.start(c) }

    @Provides @Singleton
    fun imageLoader(@ApplicationContext c: Context): coil3.ImageLoader = app.aria.music.image.AriaImageLoader.create(c)
}
