// SPDX-License-Identifier: GPL-3.0-only
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModuleGraphRulesTest {
    @Test fun validGraphHasNoViolations() {
        val graph = mapOf(
            ":domain" to emptySet<String>(),
            ":playback" to setOf(":domain"),
            ":innertube" to setOf(":domain", ":core"),
            ":app" to setOf(":playback", ":innertube", ":data", ":designsystem"),
        )
        assertEquals(emptyList<String>(), ModuleGraphRules.violations(graph))
    }

    @Test fun playbackMayNotDependOnProvidersDataOrCore() {
        val graph = mapOf(":playback" to setOf(":domain", ":data", ":innertube", ":spotify", ":lyrics", ":core"))
        assertEquals(5, ModuleGraphRules.violations(graph).size)
    }

    @Test fun domainDependsOnNothing() {
        val v = ModuleGraphRules.violations(mapOf(":domain" to setOf(":core")))
        assertEquals(1, v.size)
        assertTrue(v.single().contains(":domain -> :core"))
    }

    @Test fun unknownModuleMustGetARule() {
        val v = ModuleGraphRules.violations(mapOf(":mystery" to emptySet()))
        assertTrue(v.single().contains("no rule"))
    }
}
