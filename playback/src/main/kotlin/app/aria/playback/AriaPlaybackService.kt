// SPDX-License-Identifier: GPL-3.0-only
package app.aria.playback

import android.content.Context
import android.content.Intent
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import app.aria.domain.PlaybackStatus

@androidx.annotation.OptIn(UnstableApi::class)
class AriaPlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private var output: Media3PlayerOutput? = null
    private lateinit var engine: DefaultPlaybackEngine

    override fun onCreate() {
        super.onCreate()
        val provider = application as PlaybackEngineProvider
        engine = provider.playbackEngine
        val out = Media3PlayerOutput(this, engine, provider.allowLocalStreams).also { output = it }
        session = MediaSession.Builder(this, EngineForwardingPlayer(out.exo, engine))
            .setCallback(TrustedCallback(packageName))
            .build()
        engine.attach(out)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val status = engine.state.value.status
        if (status != PlaybackStatus.Playing && status != PlaybackStatus.Buffering) stopSelf()
    }

    override fun onDestroy() {
        output?.let { engine.detach(it) }
        session?.release(); session = null
        output?.release(); output = null
        super.onDestroy()
    }

    private class TrustedCallback(private val own: String) : MediaSession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult =
            if (TrustPolicy.isAllowed(controller.packageName, own, controller.isTrusted, session.isMediaNotificationController(controller))) {
                MediaSession.ConnectionResult.AcceptedResultBuilder(session).build()
            } else {
                MediaSession.ConnectionResult.reject()
            }
    }

    companion object {
        /** startService, not startForegroundService: Media3 promotes the service itself once playback starts. */
        fun start(context: Context) { context.startService(Intent(context, AriaPlaybackService::class.java)) }
    }
}
