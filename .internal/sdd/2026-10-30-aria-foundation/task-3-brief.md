### Task 3: Module-graph rule check

**Files:**
- Create: `buildSrc/build.gradle.kts`, `buildSrc/src/main/kotlin/ModuleGraphRules.kt`, `buildSrc/src/test/kotlin/ModuleGraphRulesTest.kt`
- Modify: `build.gradle.kts` (register `checkModuleGraph`)

**Interfaces:**
- Consumes: module paths from Task 1 settings.
- Produces: `object ModuleGraphRules { fun violations(graph: Map<String, Set<String>>): List<String> }` and Gradle task `checkModuleGraph`.

**Acceptance Criteria:**
- A graph where `:playback` depends on `:data`, `:innertube`, `:spotify`, `:lyrics` or `:core` yields one violation per offending edge.
- A module not listed in the rules yields a violation telling the author to add a rule (new modules cannot dodge the check).
- `:app` may depend on anything. `./gradlew checkModuleGraph` passes on the current tree.

- [ ] **Step 0: Create `buildSrc/build.gradle.kts`**

```kotlin
plugins { `kotlin-dsl` }

repositories { mavenCentral() }

dependencies { testImplementation("junit:junit:4.13.2") }

kotlin { jvmToolchain(21) }
```
`buildSrc` has no Android dependency on purpose, so it cannot be broken by AGP changes.

- [ ] **Step 1: Write the failing test**

`buildSrc/src/test/kotlin/ModuleGraphRulesTest.kt`:
```kotlin
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
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew -p buildSrc test`
Expected: FAIL, "Unresolved reference: ModuleGraphRules".

- [ ] **Step 3: Implement**

`buildSrc/src/main/kotlin/ModuleGraphRules.kt`:
```kotlin
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
```

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew -p buildSrc test`
Expected: PASS, 4 tests.

- [ ] **Step 5: Register the Gradle task**

Append to root `build.gradle.kts`:
```kotlin
tasks.register("checkModuleGraph") {
    group = "verification"
    description = "Fails if a module depends on one it is not allowed to."
    doLast {
        val graph = subprojects.associate { p ->
            p.path to p.configurations
                .flatMap { it.dependencies.withType(org.gradle.api.artifacts.ProjectDependency::class.java) }
                .map { it.path }
                .toSet()
        }
        val violations = ModuleGraphRules.violations(graph)
        if (violations.isNotEmpty()) throw GradleException("Module graph violations:\n" + violations.joinToString("\n"))
    }
}
```
Classes in `buildSrc` are visible to the root build script, so no extra wiring is needed.

- [ ] **Step 6: Verify and commit**

Run: `./gradlew checkModuleGraph`
Expected: `BUILD SUCCESSFUL`.

```bash
git add -A && git commit -m "build: enforce module dependency rules with checkModuleGraph"
```

---

