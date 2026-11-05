// SPDX-License-Identifier: GPL-3.0-only
package app.aria.innertube.extraction

import org.junit.Assert.assertEquals
import org.junit.Test

class FailedClientMemoryTest {
    private var now = 0L
    private val m = FailedClientMemory(ttlMs = 300_000, clock = { now })
    @Test fun remembersPerVideo() { m.record("v1", "ANDROID_VR"); assertEquals(setOf("ANDROID_VR"), m.failed("v1")); assertEquals(emptySet<String>(), m.failed("v2")) }
    @Test fun entriesExpire() { m.record("v1", "IOS"); now = 299_999; assertEquals(setOf("IOS"), m.failed("v1")); now = 300_001; assertEquals(emptySet<String>(), m.failed("v1")) }
    @Test fun accumulatesClients() { m.record("v1", "A"); m.record("v1", "B"); assertEquals(setOf("A", "B"), m.failed("v1")) }
}
