// SPDX-License-Identifier: GPL-3.0-only
package app.aria.playback

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import app.aria.domain.PlaybackEngine

/** Routes every transport command from the session, notification and headset buttons to the engine. */
@androidx.annotation.OptIn(UnstableApi::class)
class EngineForwardingPlayer(player: Player, private val engine: PlaybackEngine) : ForwardingPlayer(player) {
    override fun play() = engine.play()
    override fun pause() = engine.pause()
    override fun setPlayWhenReady(playWhenReady: Boolean) = if (playWhenReady) engine.play() else engine.pause()
    override fun seekToNext() = engine.next()
    override fun seekToNextMediaItem() = engine.next()
    override fun seekToPrevious() = engine.previous()
    override fun seekToPreviousMediaItem() = engine.previous()
    override fun seekTo(positionMs: Long) = engine.seekTo(positionMs)
    override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
        if (mediaItemIndex == currentMediaItemIndex) engine.seekTo(positionMs) else engine.skipTo(mediaItemIndex)
    }
}
