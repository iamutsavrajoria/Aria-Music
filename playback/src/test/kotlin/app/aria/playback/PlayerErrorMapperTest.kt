// SPDX-License-Identifier: GPL-3.0-only
package app.aria.playback

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import app.aria.domain.PlayerError
import app.aria.domain.StreamUnavailableException
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import kotlin.test.assertEquals

@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
class PlayerErrorMapperTest {
    @Test fun permanentInCauseChain() {
        val e = RuntimeException("wrap", IOException(PermanentStreamException(StreamUnavailableException("gone"))))
        assertEquals(PlayerError.Permanent, PlayerErrorMapper.map(e))
    }
    @Test fun httpCodeIsPreserved() {
        val http = HttpDataSource.InvalidResponseCodeException(403, "Forbidden", null, emptyMap(), DataSpec(Uri.EMPTY), ByteArray(0))
        assertEquals(PlayerError.Http(403), PlayerErrorMapper.map(RuntimeException(http)))
    }
    @Test fun everythingElseIsIo() = assertEquals(PlayerError.Io, PlayerErrorMapper.map(IOException("timeout")))
}
