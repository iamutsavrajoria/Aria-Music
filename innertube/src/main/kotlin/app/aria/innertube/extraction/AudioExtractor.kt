// SPDX-License-Identifier: GPL-3.0-only
package app.aria.innertube.extraction

interface AudioExtractor {
    /** Throws StreamUnavailableException for permanent failures; any other exception is transient. */
    suspend fun extract(videoId: String, excludeClients: Set<String>): ExtractedAudio
}
