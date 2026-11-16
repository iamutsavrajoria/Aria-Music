// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.image

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class AriaImageLoaderTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()

    @Test fun diskCacheIsCappedAndLivesInTheCacheDir() {
        assertEquals(100L * 1024 * 1024, AriaImageLoader.DISK_CACHE_BYTES)
        assertEquals(File("/c/image_cache"), AriaImageLoader.diskCacheDir(File("/c")))
    }

    @Test fun rejectedUrlsNeverReachTheNetwork() = runBlocking {
        val loader = AriaImageLoader.create(ctx, onRejectedHost = null)
        for (u in listOf("http://i.ytimg.com/x.jpg", "https://evil.example/x.png", "file:///etc/passwd")) {
            val result = loader.execute(ImageRequest.Builder(ctx).data(u).build())
            assertTrue(u, result is ErrorResult)
            assertTrue(u, (result as ErrorResult).throwable is ImageRejectedException)
        }
        loader.shutdown()
    }

    @Test fun rejectedUrlLogsTheHostOnly() = runBlocking {
        val logged = mutableListOf<String>()
        val loader = AriaImageLoader.create(ctx, onRejectedHost = { logged += it })
        val result = loader.execute(ImageRequest.Builder(ctx).data("https://evil.example/secret/path.png?token=abc").build())
        assertTrue(result is ErrorResult)
        assertEquals(listOf("evil.example"), logged)
        loader.shutdown()
    }

    @Test fun releaseStyleLoaderLogsNothing() = runBlocking {
        // onRejectedHost = null is what a release build passes; the rejection still happens.
        val loader = AriaImageLoader.create(ctx, onRejectedHost = null)
        val result = loader.execute(ImageRequest.Builder(ctx).data("https://evil.example/x.png").build())
        assertTrue(result is ErrorResult)
        loader.shutdown()
    }
}
