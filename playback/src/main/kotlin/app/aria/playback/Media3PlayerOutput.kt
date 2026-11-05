// SPDX-License-Identifier: GPL-3.0-only
package app.aria.playback

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import app.aria.domain.PlayerListener
import app.aria.domain.PlayerOutput
import app.aria.domain.QueueItem
import app.aria.domain.StreamUnavailableException
import app.aria.domain.TrackId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import java.io.IOException

/** ExoPlayer behind the engine's PlayerOutput. Items are lightweight (`aria://track/<id>`); URLs resolve at load time. */
@androidx.annotation.OptIn(UnstableApi::class)
class Media3PlayerOutput(
    context: Context,
    private val engine: DefaultPlaybackEngine,
    private val allowLocalStreams: Boolean = false,
) : PlayerOutput, Player.Listener {
    override var listener: PlayerListener? = null

    val exo: ExoPlayer = ExoPlayer.Builder(context)
        .setMediaSourceFactory(
            DefaultMediaSourceFactory(
                ResolvingDataSource.Factory(DefaultDataSource.Factory(context)) { spec -> resolve(spec) },
            ),
        )
        .setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
            /* handleAudioFocus = */ true,
        )
        .setHandleAudioBecomingNoisy(true)
        .setWakeMode(C.WAKE_MODE_NETWORK)
        .build()

    init { exo.addListener(this) }

    /** Runs on ExoPlayer's loader thread, never on the main thread. */
    private fun resolve(spec: DataSpec): DataSpec {
        if (spec.uri.scheme != SCHEME) return spec
        val raw = spec.uri.lastPathSegment ?: throw IOException("Bad track URI")
        val id = try { TrackId.parse(raw) } catch (e: IllegalArgumentException) { throw PermanentStreamException(e) }
        val stream = try {
            runBlocking { engine.streamFor(id) }.getOrThrow()
        } catch (e: CancellationException) { throw e
        } catch (e: StreamUnavailableException) { throw PermanentStreamException(e)
        } catch (e: Exception) { throw IOException(e) }
        if (!StreamUrlPolicy.isAllowed(stream.url, allowLocalStreams)) {
            throw PermanentStreamException(IllegalStateException("unsupported stream scheme"))
        }
        return spec.withUri(stream.url.toUri())
    }

    private fun item(q: QueueItem): MediaItem = MediaItem.Builder()
        .setMediaId(q.queueId.toString())
        .setUri(Uri.Builder().scheme(SCHEME).authority("track").appendPath(q.track.id.raw).build())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(q.track.title).setArtist(q.track.artists.joinToString())
                .setArtworkUri(q.track.thumbnailUrl?.toUri())
                .setIsPlayable(true).setIsBrowsable(false)
                .build(),
        ).build()

    // ---- PlayerOutput ---------------------------------------------------------------------------------
    override fun setPlaylist(items: List<QueueItem>, startIndex: Int, startPositionMs: Long, playWhenReady: Boolean) {
        exo.setMediaItems(items.map(::item), startIndex, startPositionMs)
        exo.playWhenReady = playWhenReady
        exo.prepare()
    }
    override fun insert(index: Int, items: List<QueueItem>) { exo.addMediaItems(index, items.map(::item)) }
    override fun remove(index: Int) { exo.removeMediaItem(index) }
    override fun move(from: Int, to: Int) { exo.moveMediaItem(from, to) }
    override fun reorder(items: List<QueueItem>, currentIndex: Int) {
        val cur = exo.currentMediaItemIndex
        if (cur + 1 < exo.mediaItemCount) exo.removeMediaItems(cur + 1, exo.mediaItemCount)
        if (cur > 0) exo.removeMediaItems(0, cur)
        exo.addMediaItems(0, items.subList(0, currentIndex).map(::item))
        exo.addMediaItems(exo.mediaItemCount, items.subList(currentIndex + 1, items.size).map(::item))
    }
    override fun clear() { exo.clearMediaItems() }
    override fun seekTo(index: Int, positionMs: Long) { exo.seekTo(index, positionMs) }
    override fun setPlayWhenReady(play: Boolean) { exo.playWhenReady = play }
    override fun retry(positionMs: Long) { exo.seekTo(exo.currentMediaItemIndex, positionMs); exo.prepare() }
    override fun setRepeatOne(on: Boolean) { exo.repeatMode = if (on) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF }
    override fun setSkipSilence(on: Boolean) { exo.skipSilenceEnabled = on }
    override fun setVolume(volume: Float) { exo.volume = volume }
    override fun positionMs(): Long = exo.currentPosition
    override fun durationMs(): Long = exo.duration.takeIf { it != C.TIME_UNSET } ?: 0L

    fun release() { exo.removeListener(this); exo.release() }

    // ---- Player.Listener ------------------------------------------------------------------------------
    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
            listener?.onItemTransition(exo.currentMediaItemIndex)
        }
    }
    override fun onPlaybackStateChanged(state: Int) {
        when (state) {
            Player.STATE_BUFFERING -> listener?.onBufferingChanged(true)
            Player.STATE_READY -> listener?.onBufferingChanged(false)
            Player.STATE_ENDED -> listener?.onEnded()
        }
    }
    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) { listener?.onPlayWhenReadyChanged(playWhenReady) }
    override fun onPlayerError(error: PlaybackException) { listener?.onError(PlayerErrorMapper.map(error)) }

    private companion object { const val SCHEME = "aria" }
}
