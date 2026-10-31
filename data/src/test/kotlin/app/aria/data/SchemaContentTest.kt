// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Catches silent omissions: a table or view left out of @Database does not fail compilation.
 * `expected-schema.txt` is derived from the @Entity/@DatabaseView annotations in the entities folder,
 * independently of the exported JSON, so the two can disagree and this test then fails.
 */
@RunWith(RobolectricTestRunner::class)
class SchemaContentTest {
    private fun names(): Set<String> {
        val db = JSONObject(File("schemas/app.aria.data.AriaDatabase/1.json").readText()).getJSONObject("database")
        val entities = db.getJSONArray("entities")
        val tables = (0 until entities.length()).map { "table:" + entities.getJSONObject(it).getString("tableName") }
        val viewArray = db.optJSONArray("views")
        val views = if (viewArray == null) emptyList() else
            (0 until viewArray.length()).map { "view:" + viewArray.getJSONObject(it).getString("viewName") }
        return (tables + views).toSet()
    }

    @Test fun tablesAndViewsMatchTheGoldenFile() {
        val expected = File("expected-schema.txt").readLines().filter { it.isNotBlank() }.toSet()
        assertEquals(expected, names())
    }

    @Test fun hasTwentyThreeTablesAndThreeViews() {
        val n = names()
        assertEquals(23, n.count { it.startsWith("table:") })
        assertEquals(3, n.count { it.startsWith("view:") })
    }

    @Test fun usesTheGenericMatchTableOnly() {
        val n = names()
        assertTrue("table:track_match" in n)
        assertFalse(n.any { it.contains("spotify", ignoreCase = true) || it.contains("qobuz", ignoreCase = true) })
    }
}
