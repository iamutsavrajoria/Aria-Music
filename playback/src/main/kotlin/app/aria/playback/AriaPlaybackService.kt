// SPDX-License-Identifier: GPL-3.0-only
package app.aria.playback

import android.content.Intent
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/** Minimal foreground playback service. Real queue, crossfade and downloads arrive in sub-project 2. */
class AriaPlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val exo = ExoPlayer.Builder(this).build()
        session = MediaSession.Builder(this, exo).build()
        PlaybackHost.player = Media3Player(exo)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (session?.player?.playWhenReady != true) stopSelf()
    }

    override fun onDestroy() {
        PlaybackHost.player = null
        session?.run { player.release(); release() }
        session = null
        super.onDestroy()
    }
}

/**
 * Process-local handle the app uses to reach the running service's player.
 * TODO(sub-project 2): skeleton shortcut. Replace with a bound MediaController; do not build on this.
 */
object PlaybackHost {
    @Volatile var player: Player? = null
}

class Media3Player(private val exo: ExoPlayer) : Player {
    override fun play(url: String, startPositionMs: Long) {
        exo.setMediaItem(MediaItem.fromUri(url), startPositionMs)
        exo.prepare()
        exo.play()
    }
    override fun positionMs(): Long = exo.currentPosition
    override fun stop() = exo.stop()
}
