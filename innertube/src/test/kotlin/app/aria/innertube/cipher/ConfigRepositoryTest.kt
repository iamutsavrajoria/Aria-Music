// SPDX-License-Identifier: GPL-3.0-only
package app.aria.innertube.cipher

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigRepositoryTest {
    @Test fun repositoryIsDisabledWithEmptyUrlsAndCache() {
        val r = AriaPlayerConfigRepository(null)
        assertFalse(r.enabled)
        assertEquals("", r.sourceUrl)
        assertEquals("", r.defaultSourceUrl)
        assertEquals("", r.cachedJson)
        assertEquals(0L, r.cachedAtMs)
        assertEquals("", r.cachedSourceUrl)
        assertEquals("", r.cachedEtag)
    }

    @Test fun allowedTopLevelKeysMatchLibraryParser() {
        assertEquals(setOf("schemaVersion", "players"), AriaCipherConfig.ALLOWED_TOP_LEVEL_KEYS)
    }

    @Test fun noBundledConfigAndNoForeignConfigHosts() {
        val main = File("src/main")
        assertTrue("run from the module directory", main.isDirectory)
        val forbidden = listOf("raw.github" + "usercontent.com", "zem" + "er")
        main.walkTopDown().filter { it.isFile }.forEach { f ->
            assertFalse("bundled config: $f", f.name == "player_configs.json")
            if (f.extension in setOf("kt", "kts", "json", "xml", "txt", "properties")) {
                val text = f.readText().lowercase()
                forbidden.forEach { assertFalse("$f mentions $it", text.contains(it)) }
            }
        }
    }
}
