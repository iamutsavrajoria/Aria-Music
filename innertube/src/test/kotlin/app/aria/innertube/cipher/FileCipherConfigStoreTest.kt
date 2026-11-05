// SPDX-License-Identifier: GPL-3.0-only
package app.aria.innertube.cipher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileCipherConfigStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun emptyStoreHasNoConfigAndVersionZero() {
        val s = FileCipherConfigStore(tmp.newFolder())
        assertNull(s.readConfig())
        assertEquals(0L, s.lastVersion())
    }

    @Test fun writtenValuesReadBack() {
        val s = FileCipherConfigStore(tmp.newFolder())
        s.write("""{"a":1}""", 3)
        assertEquals("""{"a":1}""", s.readConfig())
        assertEquals(3L, s.lastVersion())
    }

    @Test fun newInstanceOnSameDirSeesSameValues() {
        val dir = tmp.newFolder()
        FileCipherConfigStore(dir).write("""{"a":1}""", 3)
        val again = FileCipherConfigStore(dir)
        assertEquals("""{"a":1}""", again.readConfig())
        assertEquals(3L, again.lastVersion())
    }
}
