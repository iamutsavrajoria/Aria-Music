// SPDX-License-Identifier: GPL-3.0-only
package app.aria.innertube.adapter

import app.aria.domain.StreamUnavailableException
import app.aria.domain.TrackId
import app.aria.innertube.extraction.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class YouTubeStreamSourceTest {
    private class FakeExtractor : AudioExtractor {
        val excluded = mutableListOf<Set<String>>(); var failure: Throwable? = null; var client = "ANDROID_VR"; var expires: Long? = null; var url: String? = null
        override suspend fun extract(videoId: String, excludeClients: Set<String>): ExtractedAudio {
            excluded += excludeClients; failure?.let { throw it }
            return ExtractedAudio(url ?: "https://x/$videoId", "audio/webm", "opus", 128_000, 10L, expires, 2.0, client)
        }
    }
    private var now = 1_000L
    private val id = TrackId.youtube("dQw4w9WgXcQ")
    private val extractor = FakeExtractor()
    private val source = YouTubeStreamSource(extractor, FailedClientMemory(clock = { now }), { now })

    @Test fun defaultExpiryIsFiveMinutesWhenExtractorHasNone() = runBlocking {
        assertEquals(1_000L + 300_000L, source.resolve(id).getOrThrow().expiresAtEpochMs)
    }
    @Test fun extractorExpiryWins() = runBlocking { extractor.expires = 77L; assertEquals(77L, source.resolve(id).getOrThrow().expiresAtEpochMs) }
    @Test fun rejectedClientIsExcludedNextTime() = runBlocking {
        source.resolve(id); source.markRejected(id); extractor.client = "IOS"; source.resolve(id)
        assertEquals(emptySet<String>(), extractor.excluded[0]); assertEquals(setOf("ANDROID_VR"), extractor.excluded[1])
    }
    @Test fun permanentFailureKeepsItsType() = runBlocking {
        extractor.failure = StreamUnavailableException("private video")
        assertTrue(source.resolve(id).exceptionOrNull() is StreamUnavailableException)
    }
    @Test fun otherFailuresStayOrdinary() = runBlocking {
        extractor.failure = java.io.IOException("timeout")
        val e = source.resolve(id).exceptionOrNull(); assertTrue(e is java.io.IOException)
    }
    @Test fun nonHttpsUrlIsRejected() = runBlocking {
        extractor.url = "file:///data/data/app.aria.music/databases/aria.db"
        assertTrue(source.resolve(id).exceptionOrNull() is StreamUnavailableException)
    }
    @Test fun nonYouTubeIdsAreRejected() = runBlocking {
        assertTrue(source.resolve(TrackId.parse("local:abc")).isFailure)
    }
}
