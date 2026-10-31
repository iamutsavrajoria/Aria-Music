// SPDX-License-Identifier: GPL-3.0-only
package app.aria.innertube.adapter

import app.aria.domain.TrackId
import app.aria.innertube.models.Artist
import app.aria.innertube.models.SongItem
import app.aria.innertube.models.response.PlayerResponse.StreamingData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MappingTest {
    private fun format(itag: Int, bitrate: Int, url: String?, audio: Boolean = true) = StreamingData.Format(
        itag = itag, url = url, mimeType = "audio/webm; codecs=\"opus\"", bitrate = bitrate,
        width = if (audio) null else 1920, height = if (audio) null else 1080,
        contentLength = 1_000L, quality = "tiny", fps = null, qualityLabel = null, averageBitrate = null,
        audioQuality = "AUDIO_QUALITY_MEDIUM", approxDurationMs = null, audioSampleRate = null,
        audioChannels = null, loudnessDb = null, lastModified = null, signatureCipher = null, cipher = null,
        audioTrack = null,
    )

    @Test fun songItemMapsToTrack() {
        val item = SongItem(
            id = "dQw4w9WgXcQ", title = "Song", artists = listOf(Artist("A", "id1"), Artist("B", null)),
            duration = 213, thumbnail = "https://example.invalid/t.jpg",
        )
        val t = item.toTrack()
        assertEquals(TrackId.youtube("dQw4w9WgXcQ"), t.id)
        assertEquals(listOf("A", "B"), t.artists)
        assertEquals(213_000L, t.durationMs)
        assertEquals("https://example.invalid/t.jpg", t.thumbnailUrl)
    }

    @Test fun missingDurationStaysNull() {
        val item = SongItem(id = "dQw4w9WgXcQ", title = "S", artists = emptyList(), thumbnail = "x")
        assertNull(item.toTrack().durationMs)
    }

    @Test fun bestAudioPicksHighestBitrateWithUrl() {
        val data = StreamingData(
            formats = null, expiresInSeconds = 21_540,
            adaptiveFormats = listOf(
                format(1, 50_000, "https://a"), format(2, 160_000, "https://b"),
                format(3, 320_000, null), format(4, 900_000, "https://video", audio = false),
            ),
        )
        assertEquals(2, data.bestAudio()?.itag)
    }

    @Test fun bestAudioIsNullWhenNoUrls() {
        val data = StreamingData(null, listOf(format(1, 50_000, null)), 21_540)
        assertNull(data.bestAudio())
    }
}
