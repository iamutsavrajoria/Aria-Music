// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.image

import android.content.Context
import app.aria.music.BuildConfig
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.intercept.Interceptor
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.ErrorResult
import coil3.request.ImageResult
import coil3.size.Dimension
import coil3.size.Size
import okio.Path.Companion.toOkioPath
import timber.log.Timber
import java.io.File

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

object AriaImageLoader {
    const val DISK_CACHE_BYTES = 100L * 1024 * 1024

    /** Under cacheDir, which Android never backs up (also excluded explicitly in the backup rules). */
    fun diskCacheDir(cacheDir: File) = File(cacheDir, "image_cache")

    /** Debug builds log the rejected host (a missing CDN host shows up on the first device run); release builds log nothing. */
    private val debugHostLog: ((String) -> Unit)? =
        if (BuildConfig.DEBUG) { host -> Timber.d("Image host rejected: %s", host) } else null

    fun create(context: Context, onRejectedHost: ((String) -> Unit)? = debugHostLog): ImageLoader = ImageLoader.Builder(context)
        .components { add(SafeImageInterceptor(onRejectedHost)); add(OkHttpNetworkFetcherFactory()) }
        .diskCache { DiskCache.Builder().directory(diskCacheDir(context.cacheDir).toOkioPath()).maxSizeBytes(DISK_CACHE_BYTES).build() }
        .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.15).build() }
        .build()
}
