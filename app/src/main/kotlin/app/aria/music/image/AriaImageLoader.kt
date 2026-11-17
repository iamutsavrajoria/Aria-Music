// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.image

import android.content.Context
import app.aria.domain.ImageUrlPolicy
import app.aria.music.BuildConfig
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.serviceLoaderEnabled
import coil3.disk.DiskCache
import coil3.intercept.Interceptor
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.ErrorResult
import coil3.request.ImageResult
import coil3.size.Dimension
import coil3.size.Size
import okhttp3.HttpUrl
import okhttp3.Interceptor as OkInterceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.Path.Companion.toOkioPath
import timber.log.Timber
import java.io.File
import java.io.IOException

class ImageRejectedException(reason: String) : Exception(reason)

/**
 * Rejects URLs that fail [ImageUrlPolicy] before any network call and caps the decode size.
 * [onRejectedHost] receives the host only (never the URL); it is null in release builds.
 */
internal class SafeImageInterceptor(private val onRejectedHost: ((String) -> Unit)?) : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val data = chain.request.data
        if (data !is String || !ImageUrlPolicy.isAllowed(data)) {
            if (data is String) ImageUrlPolicy.hostOf(data)?.let { onRejectedHost?.invoke(it) }
            return ErrorResult(image = null, request = chain.request, throwable = ImageRejectedException("Image URL not allowed"))
        }
        val (w, h) = ImageSizeCap.cap((chain.size.width as? Dimension.Pixels)?.px, (chain.size.height as? Dimension.Pixels)?.px)
        return chain.withSize(Size(w, h)).proceed()
    }
}

/**
 * Gates every hop of an image fetch. The Coil interceptor only sees the original URL, so redirects are followed here by hand
 * (the client has followRedirects(false)) and each target is checked against [ImageUrlPolicy] BEFORE any connection is opened to it.
 * A network interceptor would only run after DNS and TLS to the foreign host, so this is an application interceptor.
 */
internal class RedirectGateInterceptor(private val maxHops: Int = MAX_HOPS) : OkInterceptor {
    override fun intercept(chain: OkInterceptor.Chain): Response {
        var request = chain.request()
        var hops = 0
        while (true) {
            if (!ImageUrlPolicy.isAllowed(request.url.toString())) throw IOException("Image URL not allowed")
            val response = chain.proceed(request)
            if (response.code !in REDIRECT_CODES) return response
            val target = response.header("Location")?.let { response.request.url.resolve(it) }
            response.close()
            if (target == null || ++hops > maxHops) throw IOException("Image redirect rejected")
            request = redirected(request, target)
        }
    }

    /** Redirected requests are always body-less GETs that keep only the original headers (none carry credentials here). */
    private fun redirected(original: Request, target: HttpUrl): Request =
        original.newBuilder().url(target).method("GET", null).build()

    companion object {
        const val MAX_HOPS = 5
        private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
    }
}

object AriaImageLoader {
    const val DISK_CACHE_BYTES = 100L * 1024 * 1024

    /** Under cacheDir, which Android never backs up (also excluded explicitly in the backup rules). */
    fun diskCacheDir(cacheDir: File) = File(cacheDir, "image_cache")

    /** Debug builds log the rejected host (a missing CDN host shows up on the first device run); release builds log nothing. */
    private val debugHostLog: ((String) -> Unit)? =
        if (BuildConfig.DEBUG) { host -> Timber.d("Image host rejected: %s", host) } else null

    /** [base] with redirects off and the policy gate as its first interceptor, so no later interceptor or hop can bypass it. */
    internal fun gatedClient(base: OkHttpClient = OkHttpClient()): OkHttpClient = base.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .apply { interceptors().add(0, RedirectGateInterceptor()) }
        .build()

    /** The app-wide default loader (set up by AriaApp as the Coil singleton); other code must never call SingletonImageLoader itself. */
    fun shared(context: Context): ImageLoader = SingletonImageLoader.get(context)

    fun create(context: Context, onRejectedHost: ((String) -> Unit)? = debugHostLog): ImageLoader =
        create(context, onRejectedHost, OkHttpClient())

    /** [baseClient] exists for tests (a fake network); the gate is always added in front of it. */
    internal fun create(context: Context, onRejectedHost: ((String) -> Unit)?, baseClient: OkHttpClient): ImageLoader = ImageLoader.Builder(context)
        // No service-loader components: the only fetcher is the gated one below, never a default OkHttp client.
        .serviceLoaderEnabled(false)
        .components { add(SafeImageInterceptor(onRejectedHost)); add(OkHttpNetworkFetcherFactory(gatedClient(baseClient))) }
        .diskCache { DiskCache.Builder().directory(diskCacheDir(context.cacheDir).toOkioPath()).maxSizeBytes(DISK_CACHE_BYTES).build() }
        .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.15).build() }
        .build()
}
