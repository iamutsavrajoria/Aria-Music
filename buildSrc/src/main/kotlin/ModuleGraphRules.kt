// SPDX-License-Identifier: GPL-3.0-only

/** Allowed project-to-project dependencies. `:app` is unrestricted; every other module needs a rule. */
object ModuleGraphRules {
    private val allowed: Map<String, Set<String>> = mapOf(
        ":domain" to emptySet(),
        ":metadata" to emptySet(),
        ":dsp-core" to emptySet(),
        ":designsystem" to emptySet(),
        ":core" to setOf(":domain"),
        ":data" to setOf(":domain", ":core"),
        ":playback" to setOf(":domain", ":dsp-core", ":audio-dsp", ":usbaudio"),
        ":audio-dsp" to setOf(":dsp-core"),
        ":usbaudio" to emptySet(),
        ":recognition" to setOf(":domain", ":core"),
        ":innertube" to setOf(":domain", ":core"),
        ":spotify" to setOf(":domain", ":core"),
        ":lyrics" to setOf(":domain", ":core"),
    )

    fun violations(graph: Map<String, Set<String>>): List<String> =
        graph.flatMap { (module, deps) ->
            when {
                module == ":app" -> emptyList()
                module !in allowed -> listOf("$module has no rule; add it to ModuleGraphRules")
                else -> (deps - allowed.getValue(module)).sorted().map { "$module -> $it is not allowed" }
            }
        }
}
