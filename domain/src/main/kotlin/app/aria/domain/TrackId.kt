// SPDX-License-Identifier: GPL-3.0-only
package app.aria.domain

enum class TrackSource(val prefix: String) {
    YOUTUBE(""),
    LOCAL("local:"),
    PODCAST("pod:"),
}

/**
 * The only way code builds or reads track IDs. Stored as one string: bare for YouTube (so data
 * keyed by video ID stays compatible with Meld and Echo), prefixed for every other source.
 */
@JvmInline
value class TrackId private constructor(val raw: String) {
    val source: TrackSource
        get() = PREFIXED.firstOrNull { raw.startsWith(it.prefix) } ?: TrackSource.YOUTUBE

    val value: String
        get() = raw.removePrefix(source.prefix)

    override fun toString(): String = raw

    companion object {
        private val YOUTUBE_ID = Regex("^[A-Za-z0-9_-]{11}$")
        private val PREFIXED = TrackSource.entries.filter { it.prefix.isNotEmpty() }

        fun of(source: TrackSource, value: String): TrackId {
            require(value.isNotBlank()) { "TrackId value must not be blank" }
            if (source == TrackSource.YOUTUBE) {
                require(YOUTUBE_ID.matches(value)) { "Not a YouTube video ID: $value" }
            }
            return TrackId(source.prefix + value)
        }

        fun youtube(videoId: String): TrackId = of(TrackSource.YOUTUBE, videoId)

        fun parse(raw: String): TrackId {
            val source = PREFIXED.firstOrNull { raw.startsWith(it.prefix) } ?: TrackSource.YOUTUBE
            return of(source, raw.removePrefix(source.prefix))
        }
    }
}
