// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.image

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** A fake network: records every URL it is asked to fetch and answers from [routes] (a redirect target or a 200). No real network. */
private class FakeNetwork(private val routes: Map<String, String>) : Interceptor {
    val fetched = mutableListOf<String>()
    override fun intercept(chain: Interceptor.Chain): Response {
        val url = chain.request().url.toString()
        fetched += url
        val builder = Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
        val location = routes[url]
        return if (location != null) builder.code(302).message("Found").header("Location", location).body("".toResponseBody()).build()
        else builder.code(200).message("OK").body("ok".toResponseBody()).build()
    }
}

class RedirectGateTest {
    private fun client(net: FakeNetwork): OkHttpClient = AriaImageLoader.gatedClient(OkHttpClient.Builder().addInterceptor(net).build())
    private fun fetch(c: OkHttpClient, url: String) = c.newCall(Request.Builder().url(url).build()).execute().use { it.code }

    @Test fun redirectToANonAllowlistedHostIsNeverFetched() {
        val net = FakeNetwork(mapOf("https://i.ytimg.com/a.jpg" to "https://evil.example/b.jpg"))
        assertFailsWith<IOException> { fetch(client(net), "https://i.ytimg.com/a.jpg") }
        assertEquals(listOf("https://i.ytimg.com/a.jpg"), net.fetched)
    }

    @Test fun redirectToHttpOrAllowlistLookalikeIsNeverFetched() {
        for (target in listOf("http://i.ytimg.com/b.jpg", "https://i.ytimg.com.evil.example/b.jpg", "https://i.ytimg.com:8443/b.jpg", "file:///etc/passwd")) {
            val net = FakeNetwork(mapOf("https://i.ytimg.com/a.jpg" to target))
            assertFailsWith<IOException>(target) { fetch(client(net), "https://i.ytimg.com/a.jpg") }
            assertEquals(listOf("https://i.ytimg.com/a.jpg"), net.fetched, target)
        }
    }

    @Test fun redirectBetweenAllowlistedHostsIsFollowed() {
        val net = FakeNetwork(mapOf("https://i.ytimg.com/a.jpg" to "https://lh3.googleusercontent.com/b"))
        assertEquals(200, fetch(client(net), "https://i.ytimg.com/a.jpg"))
        assertEquals(listOf("https://i.ytimg.com/a.jpg", "https://lh3.googleusercontent.com/b"), net.fetched)
    }

    @Test fun relativeRedirectStaysOnTheAllowlistedHost() {
        val net = FakeNetwork(mapOf("https://i.ytimg.com/a.jpg" to "/c.jpg"))
        assertEquals(200, fetch(client(net), "https://i.ytimg.com/a.jpg"))
        assertEquals(listOf("https://i.ytimg.com/a.jpg", "https://i.ytimg.com/c.jpg"), net.fetched)
    }

    @Test fun redirectLoopsAreCut() {
        val net = FakeNetwork(mapOf("https://i.ytimg.com/a.jpg" to "https://i.ytimg.com/a.jpg"))
        assertFailsWith<IOException> { fetch(client(net), "https://i.ytimg.com/a.jpg") }
        assertEquals(RedirectGateInterceptor.MAX_HOPS + 1, net.fetched.size)
    }

    @Test fun disallowedOriginalUrlIsNeverFetched() {
        val net = FakeNetwork(emptyMap())
        assertFailsWith<IOException> { fetch(client(net), "https://evil.example/a.jpg") }
        assertEquals(emptyList(), net.fetched)
    }
}
