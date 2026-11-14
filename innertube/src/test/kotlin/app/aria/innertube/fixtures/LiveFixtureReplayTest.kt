// SPDX-License-Identifier: GPL-3.0-only
package app.aria.innertube.fixtures

import kotlinx.serialization.json.*
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LiveFixtureReplayTest {
    private val id = Regex("^[A-Za-z0-9_-]{11}$")
    private fun walk(e: JsonElement, f: (JsonObject) -> Unit) {
        when (e) { is JsonObject -> { f(e); e.values.forEach { walk(it, f) } }; is JsonArray -> e.forEach { walk(it, f) }; else -> {} }
    }

    @Test fun capturedSummariesLookSane() {
        val dir = File(FixtureFreshness.DIR)
        val files = dir.listFiles { f -> f.name.endsWith(".json") && f.name != "manifest.json" }.orEmpty()
        for (file in files) {
            walk(Json.parseToJsonElement(file.readText())) { o ->
                if (o["type"]?.jsonPrimitive?.content == "song") assertTrue("${file.name}: bad song id", id.matches(o["id"]!!.jsonPrimitive.content))
                o["thumb"]?.takeIf { it !is JsonNull }?.let { assertTrue("${file.name}: non-https thumb", it.jsonPrimitive.content.startsWith("https://")) }
            }
        }
        if (File(dir, "home.json").exists()) assertTrue("home has no shelves", Json.parseToJsonElement(File(dir, "home.json").readText()).jsonObject["shelves"]!!.jsonArray.isNotEmpty())
    }
}
