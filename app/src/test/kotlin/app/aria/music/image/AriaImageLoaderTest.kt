// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.image

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import kotlinx.coroutines.runBlocking
import coil3.size.Size
import coil3.intercept.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
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

    /** Counts fetches and answers 404 so no image has to be decoded. */
    private val fetched = mutableListOf<String>()
    private val countingNetwork = OkHttpClient.Builder().addInterceptor(okhttp3.Interceptor { chain ->
        fetched += chain.request().url.toString()
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(404).message("Not Found").body("".toResponseBody()).build()
    }).build()

    @Test fun rejectedUrlsNeverReachTheNetwork() = runBlocking {
        val loader = AriaImageLoader.create(ctx, null, countingNetwork)
        for (u in listOf("http://i.ytimg.com/x.jpg", "https://evil.example/x.png", "file:///etc/passwd")) {
            val result = loader.execute(ImageRequest.Builder(ctx).data(u).build())
            assertTrue(u, result is ErrorResult)
            assertTrue(u, (result as ErrorResult).throwable is ImageRejectedException)
        }
        assertEquals(emptyList<String>(), fetched)
        loader.shutdown()
    }

    @Test fun allowedUrlsDoReachTheGatedNetwork() = runBlocking {
        val loader = AriaImageLoader.create(ctx, null, countingNetwork)
        loader.execute(ImageRequest.Builder(ctx).data("https://i.ytimg.com/vi/x/hq.jpg").build())
        assertEquals(listOf("https://i.ytimg.com/vi/x/hq.jpg"), fetched)
        loader.shutdown()
    }

    @Test fun allowedUrlIsResizedThroughTheCap() = runBlocking {
        var seen: Size? = null
        val req = ImageRequest.Builder(ctx).data("https://i.ytimg.com/x.jpg").size(5_000, 300).build()
        val chain = object : Interceptor.Chain {
            override val request = req
            override val size = Size(5_000, 300)
            override fun withRequest(request: ImageRequest) = this
            override fun withSize(size: Size): Interceptor.Chain {
                val resized = size
                val outer = this
                return object : Interceptor.Chain by outer {
                    override val size = resized
                    override suspend fun proceed(): coil3.request.ImageResult { seen = resized; return ErrorResult(null, req, Exception("stop")) }
                }
            }
            override suspend fun proceed(): coil3.request.ImageResult = error("must be resized first")
        }
        SafeImageInterceptor(null).intercept(chain)
        assertEquals(Size(1_024, 300), seen)
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

    @Test fun gatedClientDisablesBuiltInRedirects() {
        val client = AriaImageLoader.gatedClient(OkHttpClient())
        assertFalse(client.followRedirects)
        assertFalse(client.followSslRedirects)
        assertTrue(client.interceptors.first() is RedirectGateInterceptor)
    }

    /** Serves a 302 from an allowlisted host to a foreign host; the foreign host must never be contacted. */
    @Test fun createdLoaderNeverFollowsARedirectToAForeignHost() = runBlocking {
        val redirecting = OkHttpClient.Builder().addInterceptor(okhttp3.Interceptor { chain ->
            fetched += chain.request().url.toString()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(302).message("Found")
                .header("Location", "https://evil.example/steal.png").body("".toResponseBody()).build()
        }).build()
        val loader = AriaImageLoader.create(ctx, null, redirecting)
        val result = loader.execute(ImageRequest.Builder(ctx).data("https://i.ytimg.com/vi/x/hq.jpg").build())
        assertTrue(result is ErrorResult)
        assertEquals(listOf("https://i.ytimg.com/vi/x/hq.jpg"), fetched)
        loader.shutdown()
    }
}
