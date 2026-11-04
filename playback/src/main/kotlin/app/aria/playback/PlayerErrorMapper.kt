// SPDX-License-Identifier: GPL-3.0-only
package app.aria.playback

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import app.aria.domain.PlayerError
import java.io.IOException

/** The resolver found the stream permanently unavailable. Thrown inside ResolvingDataSource. */
class PermanentStreamException(cause: Throwable) : IOException(cause)

@androidx.annotation.OptIn(UnstableApi::class)
object PlayerErrorMapper {
    fun map(error: Throwable): PlayerError {
        var t: Throwable? = error; var depth = 0
        while (t != null && depth++ < 12) {
            if (t is PermanentStreamException) return PlayerError.Permanent
            if (t is HttpDataSource.InvalidResponseCodeException) return PlayerError.Http(t.responseCode)
            t = t.cause
        }
        return PlayerError.Io
    }
}
