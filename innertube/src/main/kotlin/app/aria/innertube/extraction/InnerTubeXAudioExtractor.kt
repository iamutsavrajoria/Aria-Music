// SPDX-License-Identifier: GPL-3.0-only
package app.aria.innertube.extraction

import app.aria.domain.StreamUnavailableException
import com.metrolist.innertubex.extraction.AudioQuality
import com.metrolist.innertubex.extraction.ContentHints
import com.metrolist.innertubex.extraction.StreamResolveException
import com.metrolist.innertubex.extraction.generateClientPlaybackNonce
import kotlinx.coroutines.CancellationException

/** Adapts InnerTubeX's extractor to [AudioExtractor]: direct (non-HLS, non-SABR) audio only. */
internal class InnerTubeXAudioExtractor(private val session: ExtractorSession) : AudioExtractor {
    override suspend fun extract(videoId: String, excludeClients: Set<String>): ExtractedAudio {
        try {
            val hints = ContentHints().withStreamCapabilities(allowHls = false, allowSabr = false, allowBoundedRange = true)
            val stream = session.extractor().extract(
                videoId = videoId,
                hints = hints,
                excludedClients = excludeClients,
                audioQuality = AudioQuality.HIGH,
                clientPlaybackNonce = generateClientPlaybackNonce(),
            ) ?: throw StreamUnavailableException("No playable stream for $videoId")
            if (stream.sabrBootstrap != null) throw StreamUnavailableException("SABR streams are not supported")
            return ExtractedAudio(
                url = stream.audioUrl,
                mimeType = stream.mimeType ?: "audio/webm",
                codecs = stream.codecs,
                bitrate = stream.bitrate ?: 0,
                contentLength = stream.contentLengthBytes,
                expiresAtEpochMs = stream.expiresAt?.toEpochMilliseconds(),
                loudnessDb = stream.loudnessDb ?: stream.perceptualLoudnessDb,
                client = stream.clientName,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: StreamResolveException) {
            val cause = e.cause
            if (e.reason == StreamResolveException.Reason.NETWORK && cause != null) throw cause
            throw StreamUnavailableException(e.message ?: "Stream unavailable")
        }
    }
}
