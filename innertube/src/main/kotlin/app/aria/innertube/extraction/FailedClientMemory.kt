// SPDX-License-Identifier: GPL-3.0-only
package app.aria.innertube.extraction

class FailedClientMemory(private val ttlMs: Long = 300_000L, private val clock: () -> Long) {
    private val failures = HashMap<String, MutableMap<String, Long>>()

    @Synchronized fun record(videoId: String, client: String) {
        failures.getOrPut(videoId) { HashMap() }[client] = clock()
    }

    @Synchronized fun failed(videoId: String): Set<String> {
        val now = clock()
        val m = failures[videoId] ?: return emptySet()
        m.entries.removeAll { now - it.value > ttlMs }
        if (m.isEmpty()) failures.remove(videoId)
        return m.keys.toSet()
    }
}
