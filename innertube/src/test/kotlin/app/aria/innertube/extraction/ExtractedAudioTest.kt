// SPDX-License-Identifier: GPL-3.0-only
package app.aria.innertube.extraction

import org.junit.Assert.assertEquals
import org.junit.Test

class ExtractedAudioTest {
    private fun audio(expires: Long? = null, codecs: String? = "opus", loud: Double? = 3.5) =
        ExtractedAudio("https://x", "audio/webm", codecs, 160_000, 1000L, expires, loud, "ANDROID_VR")
    @Test fun mapsFields() {
        val r = audio(expires = 99L).toResolvedStream()
        assertEquals("audio/webm; codecs=\"opus\"", r.mimeType); assertEquals(3.5, r.loudnessDb); assertEquals(99L, r.expiresAtEpochMs); assertEquals(160_000, r.bitrate)
    }
    @Test fun mimeWithoutCodecs() = assertEquals("audio/webm", audio(codecs = null).toResolvedStream().mimeType)
    @Test fun missingLoudnessStaysNull() = assertEquals(null, audio(loud = null).toResolvedStream().loudnessDb)
}
