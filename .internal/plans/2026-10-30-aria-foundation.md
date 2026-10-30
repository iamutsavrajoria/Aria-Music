# Aria Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use beads-superpowers:subagent-driven-development (recommended) or beads-superpowers:executing-plans to implement this plan task-by-task. Each Task becomes a bead (`bd create -t task --parent <epic-id>`). Steps within tasks use checkbox (`- [ ]`) syntax for human readability.

**Goal:** Stand up the Aria repository so that both flavors build from a clean checkout on CI, every module boundary in the spec is proven by a working walking skeleton, and the security, licensing and privacy scaffolding exists before any feature port begins.

**Architecture:** A multi-module Gradle project (pure-Kotlin `:domain`, Android libraries, one module per provider, thin `:app`). `:playback` and the UI depend only on `:domain` interfaces. YouTube access comes from a ported `:innertube` module built on InnerTubeX; everything else behind it is hidden by `SearchSource` and `StreamSource`.

**Tech Stack:** Kotlin 2.4.10, AGP 9.3.1, Gradle 9.7.0, JVM 21, Compose 1.12.0, Media3 1.10.1, Room 2.8.4, Hilt 2.60.1, Ktor 3.5.2, Coil 3.6.0, Tink (Ed25519), Robolectric 4.16.1. Versions are copied from Meld's `gradle/libs.versions.toml` at commit `fe9a666227ff3d6667075e65a4330518a724524b`, the only combination known to build together.

**Verification caveat (read first):** the sandbox this plan was written in cannot reach Maven, Google's repository, JitPack or the Gradle plugin portal, and its GitHub token is invalid, so **none of the Gradle snippets or Kotlin below has been compiled.** The pure-Kotlin logic (TrackId, registry verifier, glass resolver, crash formatter, module-graph rules, provenance script) is written test-first so each task's tests are the check. The "Expected" lines in each task are predictions until a build has run.

**Prerequisite (blocks execution):** a GitHub repository for Aria, created by the maintainer, with this session given push access (link GitHub in claude.ai settings, or provide `owner/repo` so it can be attached with push access). Until it exists, the local repo in the working directory is the only copy and can be lost with the session.

**Definition of done for every task (stress-test branch 1):** a task is done only when **CI is green for its commit** on the remote. The workflow run URL and result are pasted into the task's final commit message or PR. If CI is red, the task stays open. If a command's output differs from "Expected", fix the code, not the expectation, and say so in the commit message.

## Global Constraints

Copied from the spec; every task includes these.

- `compileSdk 37`, `targetSdk 36`, `minSdk 26`; JVM toolchain 21.
- Versions: Kotlin 2.4.10, Compose 1.12.0, Media3 1.10.1, Room 2.8.x, Ktor 3.x, Coil 3.x, Hilt 2.60.1.
- Flavors `foss` (the recommended download) and `gms` ("foss plus Google Cast", the only thing `gms` adds); ABI splits arm64 and universal.
- No Firebase, no analytics, no crash-reporting service in either flavor. No ads. Updates are notify-only.
- `:playback` depends only on `:domain` interfaces (plus DSP modules later); never on a provider module or `:data`. UI never knows which source a track came from.
- Track IDs are built and read only through `TrackId`: bare string for YouTube, prefixed for every other source.
- Module creation: the task that creates a module (`:data` in Task 4, `:designsystem` 7, `:innertube` 8, `:playback` 9, `:app` 10) also adds `include(":<module>")` to `settings.gradle.kts` in the same commit.
- Room: fresh schema at version 1, `exportSchema = true`, schema JSON committed, no legacy migrations, **no destructive-migration fallback in release builds**. Secrets (Spotify tokens, cookies) are never stored in Room.
- Every ported file keeps its original header; every ported or new file carries `SPDX-License-Identifier: GPL-3.0-only`. Both sources say "GPL-3.0" without "or later".
- Every ported directory has a row in `docs/provenance.csv`; the upstream license is audited before porting; unknown or incompatible code is not ported.
- App name, package ID and brand strings come from one config location (`aria.properties`). Placeholders: name `Aria`, ID `app.aria.music`.
- No Google, YouTube, Spotify, Apple, Qobuz or Tidal names or logos in the app name, icon or listing. Aria does not reuse Echo's or Meld's logos, banners, screenshots or seed color `0xFFED5564`.
- Release builds plant no verbose log tree; nothing logs tokens or cookies.
- Cleartext traffic disabled.
- Provider tiers: Tier 3 (paid-content circumvention, e.g. Qobuz resolvers) is excluded. No dependency on another project's private server.

## File Structure

```
aria.properties                      # name + package ID, single source
settings.gradle.kts  build.gradle.kts  gradle/libs.versions.toml
buildSrc/                            # ModuleGraphRules + its tests (added in Task 3)
domain/        pure Kotlin           TrackId, Track, SearchSource, StreamSource, PlayEventRepository, LyricsProvider
core/          Android lib           registry/ (signed registry verification), crash/ (report formatting)
data/          Android lib           AriaDatabase (Room v1), entities, DAOs, PlayEventRepositoryImpl
designsystem/  Android lib           tokens, GlassMode resolver, GlassSurface, ported backdrop code
innertube/     Android lib           ported from Meld + YouTubeSearchSource / YouTubeStreamSource
playback/      Android lib           minimal Media3 service + PlayerController
app/           Android app           Hilt wiring, skeleton screen, crash log install, backup rules
scripts/       check-provenance.py, check-backup-rules.sh
docs/          provenance.csv, providers/, data-flow.md, release-gates.md, spikes/
.github/workflows/  ci.yml, smoke.yml
```

---

### Task 0: Verification spike for external unknowns

Some spec items cannot be settled from the sandbox. This task needs network access and produces written findings; no code.

**Files:**
- Create: `docs/spikes/2026-10-innertubex.md`

**Interfaces:**
- Produces: a recorded answer to each question below, which Tasks 8 and 11 rely on.

**Acceptance Criteria:**
- The file states, with a source link for each: InnerTubeX's license and whether it is GPL-3.0 compatible; whether IzzyOnDroid and F-Droid accept a JitPack-hosted dependency (or that a pinned git submodule is needed instead); whether InnerTubeX generates PoTokens or Echo's WebView generator must be ported.
- If InnerTubeX's license is incompatible or unknown, the file says so and Task 8 is blocked until the maintainer decides.

- [ ] **Step 1: Read the license**

Run: `curl -sSL https://raw.githubusercontent.com/MetrolistGroup/innertubex/main/LICENSE | head -20`
Expected: the license text. Record its name and SPDX identifier.

- [ ] **Step 2: Check store policy on JitPack**

Read IzzyOnDroid's inclusion policy and F-Droid's build policy for dependencies hosted outside Maven Central. Record the answer and link.

- [ ] **Step 3: Check PoToken coverage**

Run: `grep -rn -i "potoken" /home/claude/francescograzioso/meld/innertube/src /home/claude/francescograzioso/meld/app/src --include=*.kt | head` and read the InnerTubeX README. Record whether PoToken generation lives in the library or in app code.

- [ ] **Step 4: Write findings and commit**

```bash
git add docs/spikes/2026-10-innertubex.md
git commit -m "docs(spike): record InnerTubeX license, store policy and PoToken findings"
```

---

### Task 1: Repository scaffold and single-source config

**Files:**
- Create: `aria.properties`, `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `.gitignore`, `LICENSE`, `README.md`, `CREDITS.md`
- Copy: `gradlew`, `gradlew.bat`, `gradle/wrapper/*` from Meld

**Interfaces:**
- Produces: the explicit per-module build-file template (Step 5); property keys `app.name`, `app.id` read by `:app`; version catalog `libs`.

**Acceptance Criteria:**
- `./gradlew help` succeeds from a clean checkout.
- Changing `app.name` in `aria.properties` is the only edit needed to rename the app label (verified in Task 10).
- README states non-affiliation with Google, YouTube and Spotify.

- [ ] **Step 1: Copy the known-good wrapper and license**

```bash
cd /home/claude/aria
M=/home/claude/francescograzioso/meld
mkdir -p gradle && cp -r $M/gradle/wrapper gradle/wrapper && cp $M/gradlew $M/gradlew.bat . && cp $M/LICENSE LICENSE
grep distributionUrl gradle/wrapper/gradle-wrapper.properties
```
Expected: `distributionUrl=https\://services.gradle.org/distributions/gradle-9.7.0-bin.zip`

- [ ] **Step 2: Start the catalog from Meld's and add Tink**

```bash
cp $M/gradle/libs.versions.toml gradle/libs.versions.toml
sed -i 's/^protobuf = "4.36.0"$/protobuf = "4.36.0"\ntink = "1.15.0"/' gradle/libs.versions.toml
sed -i '0,/^\[libraries\]$/s//[libraries]\ntink-android = { module = "com.google.crypto.tink:tink-android", version.ref = "tink" }/' gradle/libs.versions.toml
grep -n -E '^tink|tink-android' gradle/libs.versions.toml
```
Expected: two matching lines. (Unused catalog entries are harmless; trim them at Task 12.)

- [ ] **Step 3: Write `aria.properties`**

```properties
# Single source of truth for brand and identity. Changing these must be enough to rename the app.
# Both values are PLACEHOLDERS until the name and package-ID gate in docs/release-gates.md is passed.
app.name=Aria
app.id=app.aria.music
```

- [ ] **Step 4: Write `settings.gradle.kts`**

```kotlin
@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

val useMavenLocalInnerTubeX = providers.gradleProperty("useMavenLocalInnerTubeX").isPresent

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        exclusiveContent {
            forRepository { if (useMavenLocalInnerTubeX) mavenLocal() else maven("https://jitpack.io") }
            filter {
                if (useMavenLocalInnerTubeX) {
                    includeModule("com.github.MetrolistGroup", "innertubex")
                    includeModule("com.github.MetrolistGroup", "innertubex-android")
                    includeModule("com.github.MetrolistGroup", "innertubex-desktop")
                } else {
                    includeGroup("com.github.MetrolistGroup.innertubex")
                }
            }
        }
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

rootProject.name = "Aria"
// Only modules that exist are included (Gradle 9 rejects an included project without a directory).
// Each task that creates a module adds its own include(...) line in the same commit.
include(":domain", ":core")
```

- [ ] **Step 5: Define the explicit module build-file template**

There are no convention plugins in the foundation (stress-test branch 2): precompiled script plugins under AGP 9 are untested here, while Meld's explicit module files are known to build under AGP 9.3.1 and Gradle 9.7.0. Every Android library module's `build.gradle.kts` starts from this template, shown in full in each task:
```kotlin
plugins { id("com.android.library") /* + other plugins */ }

android {
    namespace = "app.aria.<module>"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

kotlin { jvmToolchain(21) }
```
The pure-Kotlin `:domain` uses `id("org.jetbrains.kotlin.jvm")`, `kotlin { jvmToolchain(21) }` and `tasks.withType<Test> { useJUnit() }`. The template mirrors `/home/claude/francescograzioso/meld/innertube/build.gradle.kts`. Converting the repetition into convention plugins is an optional refactor after CI is green and the walking skeleton works; it is not part of this plan.

- [ ] **Step 6: Write root `build.gradle.kts` and `.gitignore`**

```kotlin
plugins {
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.kotlin.ksp) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
```

`.gitignore`:
```
.gradle/
build/
local.properties
*.keystore
*.jks
.idea/
captures/
.cxx/
.internal/stress-tests/
```

- [ ] **Step 7: Write README and CREDITS**

`README.md`:
```markdown
# Aria

An Android music client that combines YouTube Music playback with Spotify-driven discovery. GPL-3.0-only.

Aria is an independent project. It is **not affiliated with, endorsed by, or sponsored by** Google, YouTube, YouTube Music, Spotify, or any other service it connects to. All trademarks belong to their owners.

Status: foundation under construction. See `.internal/specs/` and `.internal/plans/`.

Credits: see [CREDITS.md](CREDITS.md). Provenance of ported code: [docs/provenance.csv](docs/provenance.csv).
```

`CREDITS.md`:
```markdown
# Credits

Aria builds on the work of other open-source projects. Per module: what was taken and whether it was copied, adapted, or only inspired. The authoritative per-directory record is `docs/provenance.csv`.

| Origin | What | How |
|---|---|---|
| [Meld](https://github.com/FrancescoGrazioso/Meld) (GPL-3.0) | `:innertube` module; Spotify integration design (later) | See `docs/provenance.csv` for copied vs adapted, per directory |
| [Echo Music](https://github.com/EchoMusicApp/Echo-Music) (GPL-3.0) | Room entities; Liquid Glass backdrop code | See `docs/provenance.csv` for copied vs adapted, per directory |
| [Metrolist](https://github.com/MetrolistGroup/Metrolist) | Common ancestor of Meld and Echo | Indirect |
```

- [ ] **Step 8: Add a minimal CI and a foundation proof**

`.github/workflows/ci.yml` (Task 11 extends this file; it does not replace it):
```yaml
name: ci
on:
  push: { branches: [main] }
  pull_request:
permissions: { contents: read }
jobs:
  verify:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: 21 }
      - uses: gradle/actions/setup-gradle@v4
      - run: ./gradlew build test
```
Create the smallest proof that the build setup works: a `:domain` placeholder file `domain/src/main/kotlin/app/aria/domain/Placeholder.kt` is **not** acceptable; instead build Task 2's `:domain` and a trivial Android library together as the first push. Concretely, finish Task 2 Steps 1–5 before pushing, and include `:core` with an empty `core/build.gradle.kts` (the Android library template with `namespace = "app.aria.core"`). Then both module templates (pure Kotlin and Android library) are exercised by CI.

- [ ] **Step 9: Verify, push, and confirm CI**

Run locally if a build environment exists: `./gradlew build test`
Expected: `BUILD SUCCESSFUL`. Then push and confirm the `ci` workflow is green. **If it is red, stop and fix the foundation before starting Task 3.** Compare against `/home/claude/francescograzioso/meld/innertube/build.gradle.kts` and the Meld root `build.gradle.kts` first.

```bash
git add -A && git commit -m "build: scaffold Gradle project, catalog, single-source config, minimal CI"
```

---

### Task 2: `:domain` module (TrackId and interfaces)

**Files:**
- Create: `domain/build.gradle.kts`, `domain/src/main/kotlin/app/aria/domain/TrackId.kt`, `domain/src/main/kotlin/app/aria/domain/Sources.kt`, `domain/src/test/kotlin/app/aria/domain/TrackIdTest.kt`

**Interfaces:**
- Produces:
  - `enum class TrackSource(val prefix: String) { YOUTUBE(""), LOCAL("local:"), PODCAST("pod:") }`
  - `@JvmInline value class TrackId` with `val raw: String`, `val source: TrackSource`, `val value: String`; `TrackId.of(source, value)`, `TrackId.youtube(videoId)`, `TrackId.parse(raw)`
  - `data class Track(id: TrackId, title: String, artists: List<String>, durationMs: Long?, thumbnailUrl: String?)`
  - `data class ResolvedStream(url: String, mimeType: String, bitrate: Int, contentLength: Long?, expiresAtEpochMs: Long?)`
  - `class StreamUnavailableException(message: String) : Exception(message)`
  - `interface SearchSource { suspend fun searchTracks(query: String): Result<List<Track>> }`
  - `interface StreamSource { suspend fun resolve(id: TrackId): Result<ResolvedStream> }`
  - `interface PlayEventRepository { suspend fun record(track: Track, playedAtEpochMs: Long, playTimeMs: Long); suspend fun playCount(id: TrackId): Int }`
  - `interface LyricsProvider { val id: String; suspend fun lyrics(track: Track): Result<String?> }`

**Acceptance Criteria:**
- `TrackId.parse("dQw4w9WgXcQ").source == TrackSource.YOUTUBE`; `TrackId.parse("local:abc").source == LOCAL`; round-trip `parse(id.raw) == id` for every source.
- A bare string that is not an 11-character YouTube ID is rejected; an empty prefixed value is rejected.
- `:domain` has no Android or project dependencies.

- [ ] **Step 1: Write `domain/build.gradle.kts`**

```kotlin
plugins { id("org.jetbrains.kotlin.jvm") }

kotlin { jvmToolchain(21) }

tasks.withType<Test> { useJUnit() }

dependencies {
    testImplementation(kotlin("test"))
}
```

- [ ] **Step 2: Write the failing tests**

`domain/src/test/kotlin/app/aria/domain/TrackIdTest.kt`:
```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TrackIdTest {
    @Test fun bareElevenCharIdIsYoutube() {
        val id = TrackId.parse("dQw4w9WgXcQ")
        assertEquals(TrackSource.YOUTUBE, id.source)
        assertEquals("dQw4w9WgXcQ", id.value)
        assertEquals("dQw4w9WgXcQ", id.raw)
    }

    @Test fun prefixedIdsKeepTheirSource() {
        assertEquals(TrackSource.LOCAL, TrackId.parse("local:abc").source)
        assertEquals("abc", TrackId.parse("local:abc").value)
        assertEquals(TrackSource.PODCAST, TrackId.parse("pod:xyz").source)
    }

    @Test fun roundTripsForEverySource() {
        val ids = listOf(
            TrackId.youtube("dQw4w9WgXcQ"),
            TrackId.of(TrackSource.LOCAL, "file-1"),
            TrackId.of(TrackSource.PODCAST, "ep-9"),
        )
        ids.forEach { assertEquals(it, TrackId.parse(it.raw)) }
    }

    @Test fun rejectsBareStringThatIsNotAYoutubeId() {
        assertFailsWith<IllegalArgumentException> { TrackId.parse("local_track_1") }
        assertFailsWith<IllegalArgumentException> { TrackId.parse("short") }
    }

    @Test fun rejectsEmptyPrefixedValue() {
        assertFailsWith<IllegalArgumentException> { TrackId.parse("local:") }
        assertFailsWith<IllegalArgumentException> { TrackId.of(TrackSource.PODCAST, " ") }
    }
}
```

- [ ] **Step 3: Run to verify failure**

Run: `./gradlew :domain:test`
Expected: FAIL, compilation error "Unresolved reference: TrackId".

- [ ] **Step 4: Implement**

`domain/src/main/kotlin/app/aria/domain/TrackId.kt`:
```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.domain

enum class TrackSource(val prefix: String) {
    YOUTUBE(""),
    LOCAL("local:"),
    PODCAST("pod:"),
}

/**
 * The only way code builds or reads track IDs. Stored as one string: bare for YouTube (so data
 * keyed by video ID stays compatible with Meld and Echo), prefixed for every other source.
 */
@JvmInline
value class TrackId private constructor(val raw: String) {
    val source: TrackSource
        get() = PREFIXED.firstOrNull { raw.startsWith(it.prefix) } ?: TrackSource.YOUTUBE

    val value: String
        get() = raw.removePrefix(source.prefix)

    override fun toString(): String = raw

    companion object {
        private val YOUTUBE_ID = Regex("^[A-Za-z0-9_-]{11}$")
        private val PREFIXED = TrackSource.entries.filter { it.prefix.isNotEmpty() }

        fun of(source: TrackSource, value: String): TrackId {
            require(value.isNotBlank()) { "TrackId value must not be blank" }
            if (source == TrackSource.YOUTUBE) {
                require(YOUTUBE_ID.matches(value)) { "Not a YouTube video ID: $value" }
            }
            return TrackId(source.prefix + value)
        }

        fun youtube(videoId: String): TrackId = of(TrackSource.YOUTUBE, videoId)

        fun parse(raw: String): TrackId {
            val source = PREFIXED.firstOrNull { raw.startsWith(it.prefix) } ?: TrackSource.YOUTUBE
            return of(source, raw.removePrefix(source.prefix))
        }
    }
}
```

`domain/src/main/kotlin/app/aria/domain/Sources.kt`:
```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.domain

data class Track(
    val id: TrackId,
    val title: String,
    val artists: List<String>,
    val durationMs: Long?,
    val thumbnailUrl: String?,
)

data class ResolvedStream(
    val url: String,
    val mimeType: String,
    val bitrate: Int,
    val contentLength: Long?,
    val expiresAtEpochMs: Long?,
)

class StreamUnavailableException(message: String) : Exception(message)

interface SearchSource {
    suspend fun searchTracks(query: String): Result<List<Track>>
}

interface StreamSource {
    suspend fun resolve(id: TrackId): Result<ResolvedStream>
}

interface PlayEventRepository {
    suspend fun record(track: Track, playedAtEpochMs: Long, playTimeMs: Long)
    suspend fun playCount(id: TrackId): Int
}

interface LyricsProvider {
    val id: String
    suspend fun lyrics(track: Track): Result<String?>
}
```

- [ ] **Step 5: Run to verify pass**

Run: `./gradlew :domain:test`
Expected: `BUILD SUCCESSFUL`, 5 tests passed.

- [ ] **Step 6: Commit**

```bash
git add domain && git commit -m "feat(domain): add TrackId, track models and source interfaces"
```

---

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

### Task 4: `:data` Room v1 foundation and migration harness

**Files:**
- Create: `data/build.gradle.kts`, `data/src/main/kotlin/app/aria/data/AriaDatabase.kt`, `data/src/main/kotlin/app/aria/data/Converters.kt`, `data/src/main/kotlin/app/aria/data/entities/TrackMatchEntity.kt`, `data/src/main/kotlin/app/aria/data/TrackMatchDao.kt`, `data/src/test/kotlin/app/aria/data/TrackMatchDaoTest.kt`, `data/src/test/kotlin/app/aria/data/MigrationHarnessTest.kt`, `data/schemas/` (generated)

**Interfaces:**
- Consumes: `TrackSource` conventions from Task 2.
- Produces: `@Database(version = 1, exportSchema = true) abstract class AriaDatabase : RoomDatabase()`; `TrackMatchEntity(sourceProvider, sourceId, targetProvider, targetId, score, isManualOverride, cachedAt)`; `TrackMatchDao { upsertAutomatic, upsertManual, find, findReverse }`; `Converters` (LocalDateTime stored as UTC epoch millis).

**Acceptance Criteria:**
- `track_match` has primary key (`sourceProvider`, `sourceId`, `targetProvider`) and an index for reverse lookup on (`targetProvider`, `targetId`).
- An automatic match never overwrites a manual override; a manual override replaces any automatic one.
- `data/schemas/app.aria.data.AriaDatabase/1.json` is committed and the Robolectric harness opens it with `MigrationTestHelper` and validates it.
- The release build type does not call `fallbackToDestructiveMigration`.

- [ ] **Step 1: Write `data/build.gradle.kts`**

```kotlin
plugins {
    id("com.android.library")
    alias(libs.plugins.kotlin.ksp)
}

android {
    namespace = "app.aria.data"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
    sourceSets.getByName("test").assets.srcDir("$projectDir/schemas")
}

kotlin { jvmToolchain(21) }

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    api(project(":domain"))
    implementation(project(":core"))
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.room.testing)
    testImplementation(libs.coroutines.test)
}
```
Run `grep -n -E "room|robolectric|androidx-test|coroutines" gradle/libs.versions.toml` and add any missing alias (`room-testing`, `coroutines-test`, `androidx-test-core`) to the catalog with the version already pinned there.

- [ ] **Step 2: Write the failing DAO test**

`data/src/test/kotlin/app/aria/data/TrackMatchDaoTest.kt`:
```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.aria.data.entities.TrackMatchEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TrackMatchDaoTest {
    private lateinit var db: AriaDatabase
    private val dao get() = db.trackMatchDao()

    @Before fun open() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AriaDatabase::class.java).allowMainThreadQueries().build()
    }

    @After fun close() = db.close()

    private fun match(target: String, score: Double, manual: Boolean = false) = TrackMatchEntity(
        sourceProvider = "spotify", sourceId = "sp1", targetProvider = "youtube", targetId = target,
        score = score, isManualOverride = manual, cachedAt = 1L,
    )

    @Test fun automaticMatchIsStoredAndFound() = runBlocking {
        dao.upsertAutomatic(match("AAAAAAAAAAA", 0.9))
        assertEquals("AAAAAAAAAAA", dao.find("spotify", "sp1", "youtube")?.targetId)
    }

    @Test fun automaticNeverOverwritesManual() = runBlocking {
        dao.upsertManual(match("MANUAL_ID_1", 1.0, manual = true))
        dao.upsertAutomatic(match("AUTO_ID_222", 0.95))
        val found = dao.find("spotify", "sp1", "youtube")!!
        assertEquals("MANUAL_ID_1", found.targetId)
        assertEquals(true, found.isManualOverride)
    }

    @Test fun manualReplacesAutomatic() = runBlocking {
        dao.upsertAutomatic(match("AUTO_ID_222", 0.95))
        dao.upsertManual(match("MANUAL_ID_1", 1.0, manual = true))
        assertEquals("MANUAL_ID_1", dao.find("spotify", "sp1", "youtube")?.targetId)
    }

    @Test fun reverseLookupFindsSourceByTarget() = runBlocking {
        dao.upsertAutomatic(match("AAAAAAAAAAA", 0.9))
        assertEquals("sp1", dao.findReverse("youtube", "AAAAAAAAAAA", "spotify")?.sourceId)
        assertNull(dao.findReverse("youtube", "ZZZZZZZZZZZ", "spotify"))
    }
}
```

- [ ] **Step 3: Run to verify failure**

Run: `./gradlew :data:testDebugUnitTest --tests '*TrackMatchDaoTest'`
Expected: FAIL, unresolved references to `AriaDatabase` and `TrackMatchEntity`.

- [ ] **Step 4: Implement entity, DAO, converters, database**

`data/src/main/kotlin/app/aria/data/entities/TrackMatchEntity.kt`:
```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * One generic table replacing Meld's `spotify_match` and `qobuz_match`. Maps a track on one
 * provider to its best match on another. A manual override is chosen by the user and must never
 * be replaced by the automatic matcher.
 */
@Entity(
    tableName = "track_match",
    primaryKeys = ["sourceProvider", "sourceId", "targetProvider"],
    indices = [Index(value = ["targetProvider", "targetId"])],
)
data class TrackMatchEntity(
    val sourceProvider: String,
    val sourceId: String,
    val targetProvider: String,
    val targetId: String,
    val score: Double,
    @ColumnInfo(defaultValue = "0") val isManualOverride: Boolean = false,
    val cachedAt: Long = System.currentTimeMillis(),
)
```

`data/src/main/kotlin/app/aria/data/TrackMatchDao.kt`:
```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import app.aria.data.entities.TrackMatchEntity

@Dao
abstract class TrackMatchDao {
    @Query("SELECT * FROM track_match WHERE sourceProvider = :sp AND sourceId = :sid AND targetProvider = :tp")
    abstract suspend fun find(sp: String, sid: String, tp: String): TrackMatchEntity?

    @Query("SELECT * FROM track_match WHERE targetProvider = :tp AND targetId = :tid AND sourceProvider = :sp LIMIT 1")
    abstract suspend fun findReverse(tp: String, tid: String, sp: String): TrackMatchEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insert(entity: TrackMatchEntity)

    @Transaction
    open suspend fun upsertAutomatic(entity: TrackMatchEntity) {
        val existing = find(entity.sourceProvider, entity.sourceId, entity.targetProvider)
        if (existing?.isManualOverride == true) return
        insert(entity.copy(isManualOverride = false))
    }

    @Transaction
    open suspend fun upsertManual(entity: TrackMatchEntity) = insert(entity.copy(isManualOverride = true))
}
```

`data/src/main/kotlin/app/aria/data/Converters.kt`:
```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data

import androidx.room.TypeConverter
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/** LocalDateTime is stored as UTC epoch millis. (Aria's schema is new, so UTC is chosen explicitly.) */
class Converters {
    @TypeConverter fun fromEpoch(value: Long?): LocalDateTime? =
        value?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneOffset.UTC) }

    @TypeConverter fun toEpoch(value: LocalDateTime?): Long? =
        value?.toInstant(ZoneOffset.UTC)?.toEpochMilli()
}
```

`data/src/main/kotlin/app/aria/data/AriaDatabase.kt`:
```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import app.aria.data.entities.TrackMatchEntity

/**
 * Schema version 1. Until the first public build the exported JSON in `schemas/` is regenerated
 * freely; from the first beta on it is frozen and every change needs a tested migration.
 * Release builds must never use destructive migration fallback.
 */
@Database(entities = [TrackMatchEntity::class], version = 1, exportSchema = true)
@TypeConverters(Converters::class)
abstract class AriaDatabase : RoomDatabase() {
    abstract fun trackMatchDao(): TrackMatchDao
}
```

- [ ] **Step 5: Run to verify pass**

Run: `./gradlew :data:testDebugUnitTest --tests '*TrackMatchDaoTest'`
Expected: PASS, 4 tests. `data/schemas/app.aria.data.AriaDatabase/1.json` now exists.

- [ ] **Step 6: Add the migration harness test**

`data/src/test/kotlin/app/aria/data/MigrationHarnessTest.kt`:
```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Template for every future migration test: create the old version from its exported JSON, migrate, validate. */
@RunWith(RobolectricTestRunner::class)
class MigrationHarnessTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AriaDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test fun version1SchemaOpensAndValidates() {
        helper.createDatabase("aria-test", 1).close()
        helper.runMigrationsAndValidate("aria-test", 1, true).close()
    }
}
```

- [ ] **Step 7: Guard against destructive fallback**

Create `scripts/check-no-destructive-migration.sh`:
```bash
#!/usr/bin/env bash
# Fails if production code calls fallbackToDestructiveMigration.
if grep -rn "fallbackToDestructiveMigration" --include=*.kt --exclude-dir=test --exclude-dir=androidTest .; then
  echo "ERROR: destructive migration fallback found in production code" >&2
  exit 1
fi
```
Run: `chmod +x scripts/check-no-destructive-migration.sh && scripts/check-no-destructive-migration.sh`
Expected: exit 0, no output.

- [ ] **Step 8: Run all data tests and commit**

Run: `./gradlew :data:testDebugUnitTest`
Expected: PASS, 5 tests.

```bash
git add -A && git commit -m "feat(data): add Room v1 with generic track_match table and migration harness"
```

---

### Task 5: Port the lineage entities and implement `PlayEventRepository`

The shared core tables come from Echo (and Meld for podcast and artist page cache). This task ports them with a recorded license audit and implements the walking skeleton's play-event repository on the ported `song` and `event` tables.

**Files:**
- Create: `scripts/port-scan.sh`, `data/src/main/kotlin/app/aria/data/entities/*` (ported, see Step 3), `data/src/main/kotlin/app/aria/data/PlayEventDao.kt`, `data/src/main/kotlin/app/aria/data/PlayEventRepositoryImpl.kt`, `data/src/test/kotlin/app/aria/data/PlayEventRepositoryTest.kt`, `data/src/test/kotlin/app/aria/data/SchemaContentTest.kt`, `data/expected-schema.txt`
- Modify: `data/src/main/kotlin/app/aria/data/AriaDatabase.kt`, `docs/provenance.csv`

**Interfaces:**
- Consumes: `PlayEventRepository`, `Track`, `TrackId` (Task 2); `AriaDatabase`, `Converters` (Task 4).
- Produces: `PlayEventRepositoryImpl(dao: PlayEventDao) : PlayEventRepository`; ported entities `SongEntity`, `Event` and the rest of the list in Step 3.

**Acceptance Criteria:**
- Every ported file's origin license is recorded in `docs/provenance.csv` before the port; files with no header in the source (e.g. Echo's `Event.kt`, which has none) get `SPDX-License-Identifier: GPL-3.0-only` and a ledger row saying the upstream header was absent.
- `record()` on an unknown track inserts the song row and one event row; a second call adds a second event; `playCount` returns 2.
- `AriaDatabase` is still version 1; schema JSON regenerated and committed.
- No Spotify or Qobuz match tables are ported (superseded by `track_match`).
- `scripts/port-scan.sh data/src/main/kotlin/app/aria/data/entities` reports 0 foreign imports (stop rule: more than 10 distinct foreign imports means stop and ask the maintainer).
- `AriaDatabase` registers the 24 ported and new tables **and** the three views (`SortedSongArtistMap`, `SortedSongAlbumMap`, `PlaylistSongMapPreview`); `SchemaContentTest` pins the exact table and view names to `data/expected-schema.txt`.
- `MetadataExtensions.kt` is deliberately **not** ported: it is the only file in Echo's entities folder that imports Echo's `:metadata` cleaner types (`MetadataCleaner`, `CleanedMetadata`). Revisit it when the metadata module is ported.

- [ ] **Step 1: Audit upstream licenses first**

```bash
E=/home/claude/echomusicapp/echo-music/core/src/main/kotlin/echo/music/iad1tya/db/entities
M=/home/claude/francescograzioso/meld/app/src/main/kotlin/com/metrolist/music/db/entities
for f in $E/*.kt; do printf "%-40s %s\n" "$(basename $f)" "$(head -6 $f | grep -o -i -E 'GPL-3.0|Apache License|MIT' | head -1 || true)"; done
for f in PodcastEntity ArtistPageCache; do printf "%-40s %s\n" "$f" "$(head -6 $M/$f.kt | grep -o -i -E 'GPL-3.0|Apache License|MIT' | head -1)"; done
```
Record the result per file. A file whose header names a license other than GPL-3.0 stops the port of that file until the maintainer decides. Files with no header are covered by the repository-level GPL-3.0 and the ledger notes "no upstream header".

- [ ] **Step 2: Write the ledger rows**

`docs/provenance.csv` (create if absent; header first line):
```csv
path,origin_repo,origin_commit,origin_license,kind,notes
domain,,,,original,TrackId and source interfaces written for Aria
data/src/main/kotlin/app/aria/data/entities,EchoMusicApp/Echo-Music,b04d7507b33c0671b3a23d2658b24a376add4cc7,GPL-3.0,adapted,Entities ported from core/db/entities; many files had no upstream header; package renamed; ids are TrackId strings
```
Add one row per additional origin (Meld `PodcastEntity`, `ArtistPageCache` at `fe9a666227ff3d6667075e65a4330518a724524b`).

- [ ] **Step 3: Port the entities**

Entities to port from Echo `core/src/main/kotlin/echo/music/iad1tya/db/entities/` (current names): `SongEntity`, `Song`, `AlbumEntity`, `Album`, `AlbumWithSongs`, `AlbumArtistMap`, `ArtistEntity`, `Artist`, `SongArtistMap`, `SongAlbumMap`, `SortedSongArtistMap`, `SortedSongAlbumMap`, `PlaylistEntity`, `Playlist`, `PlaylistSong`, `PlaylistSongMap`, `PlaylistSongMapPreview`, `LyricsEntity`, `FormatEntity`, `PlayCountEntity`, `Event`, `EventWithSong`, `RelatedSongMap`, `SearchHistory`, `SpeedDialItem`, `SetVideoIdEntity`, `RecognitionHistory`, `LocalItem`, `SongWithStats`, `BeatInfoEntity`, `RecommendationExclusionEntity`, `SongPlayStatsEntity`, `TasteProfileEntity`. From Meld `app/src/main/kotlin/com/metrolist/music/db/entities/`: `PodcastEntity`, `ArtistPageCache`. **Do not port** `SpotifyMatchEntity` or `QobuzMatchEntity` (replaced by `track_match`).

```bash
DEST=/home/claude/aria/data/src/main/kotlin/app/aria/data/entities
for n in SongEntity Song AlbumEntity Album AlbumWithSongs AlbumArtistMap ArtistEntity Artist SongArtistMap SongAlbumMap SortedSongArtistMap SortedSongAlbumMap PlaylistEntity Playlist PlaylistSong PlaylistSongMap PlaylistSongMapPreview LyricsEntity FormatEntity PlayCountEntity Event EventWithSong RelatedSongMap SearchHistory SpeedDialItem SetVideoIdEntity RecognitionHistory LocalItem SongWithStats BeatInfoEntity RecommendationExclusionEntity SongPlayStatsEntity TasteProfileEntity; do
  cp "$E/$n.kt" "$DEST/$n.kt"
done
cp $M/PodcastEntity.kt $M/ArtistPageCache.kt $DEST/
sed -i 's/^package echo\.music\.iad1tya\.db\.entities/package app.aria.data.entities/; s/^package com\.metrolist\.music\.db\.entities/package app.aria.data.entities/' $DEST/*.kt
sed -i 's/echo\.music\.iad1tya\.db\.entities/app.aria.data.entities/g; s/com\.metrolist\.music\.db\.entities/app.aria.data.entities/g' $DEST/*.kt
# SPDX header on every file that does not have a license header already
for f in $DEST/*.kt; do grep -q -E "SPDX-License-Identifier|Licensed under" "$f" || sed -i '1i // SPDX-License-Identifier: GPL-3.0-only' "$f"; done
grep -rn "androidx.compose" $DEST | head
```
Expected: the final grep may show `androidx.compose.runtime.Immutable` imports (Echo's `Event` uses `@Immutable`). Either add `implementation(libs.compose.runtime)` to `:data` or delete the annotation and import; choose the dependency-free option (delete both). Then fix any remaining unresolved references by reading the compiler errors; entities referring to types outside the ported set (for example `Artist` extension helpers) are ported with the same procedure or removed if only used by Echo's UI.

- [ ] **Step 3b: Scan for foreign imports (stop rule)**

Create `scripts/port-scan.sh` (used again in Tasks 7 and 8):
```bash
#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-only
# Lists imports in ported code that still point into the source projects, and fails above a threshold.
# usage: scripts/port-scan.sh <dir> [max-distinct=10]
set -euo pipefail
dir=${1:?usage: port-scan.sh <dir> [max-distinct]}
max=${2:-10}
foreign='^(echo\.music|com\.music\.echo|com\.metrolist\.(music|spotify|innertube\b))'
found=$(grep -rhoE '^import [A-Za-z0-9_.]+' "$dir" --include=*.kt | sed 's/^import //' | grep -E "$foreign" | sort -u || true)
count=$(printf '%s' "$found" | grep -c . || true)
[ -n "$found" ] && printf '%s\n' "$found"
echo "distinct foreign imports: $count (limit $max)"
if [ "$count" -gt "$max" ]; then echo "STOP: scope is larger than planned; report to the maintainer" >&2; exit 1; fi
```
Run: `chmod +x scripts/port-scan.sh && scripts/port-scan.sh data/src/main/kotlin/app/aria/data/entities`
Expected: `distinct foreign imports: 0 (limit 10)`. (Dry run on the planned file set against Echo at the pinned commit gave 0; the only file with foreign imports, `MetadataExtensions.kt`, is excluded.) If the count is between 1 and 10, resolve each import by porting the file it names (add a ledger row) or removing the usage. If it is above 10, **stop and report to the maintainer**.

- [ ] **Step 4: Register entities in `AriaDatabase`**

Replace the `@Database` line with the full entity list (ported names above plus `TrackMatchEntity`), keeping `version = 1`, and add DAOs as needed:
```kotlin
@Database(
    entities = [
        TrackMatchEntity::class, SongEntity::class, Event::class, AlbumEntity::class, ArtistEntity::class,
        PlaylistEntity::class, PlaylistSongMap::class, SongArtistMap::class, SongAlbumMap::class,
        AlbumArtistMap::class, LyricsEntity::class, FormatEntity::class, PlayCountEntity::class,
        RelatedSongMap::class, SearchHistory::class, SpeedDialItem::class, SetVideoIdEntity::class,
        RecognitionHistory::class, BeatInfoEntity::class, RecommendationExclusionEntity::class,
        SongPlayStatsEntity::class, TasteProfileEntity::class, PodcastEntity::class, ArtistPageCache::class,
    ],
    views = [SortedSongArtistMap::class, SortedSongAlbumMap::class, PlaylistSongMapPreview::class],
    version = 1,
    exportSchema = true,
)
```
Add `abstract fun playEventDao(): PlayEventDao`. The three views are registered explicitly because Room does not complain about a view that is simply left out; queries that need them would only fail later.

- [ ] **Step 4b: Pin the schema contents**

`data/src/test/kotlin/app/aria/data/SchemaContentTest.kt`:
```kotlin
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

/** Catches silent omissions: a table or view left out of @Database does not fail compilation. */
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

    @Test fun hasTwentyFourTablesAndThreeViews() {
        val n = names()
        assertEquals(24, n.count { it.startsWith("table:") })
        assertEquals(3, n.count { it.startsWith("view:") })
    }

    @Test fun usesTheGenericMatchTableOnly() {
        val n = names()
        assertTrue("table:track_match" in n)
        assertFalse(n.any { it.contains("spotify", ignoreCase = true) || it.contains("qobuz", ignoreCase = true) })
    }
}
```
Run: `./gradlew :data:testDebugUnitTest --tests '*SchemaContentTest'`
Expected: FAIL, because `data/expected-schema.txt` does not exist yet (the build has just regenerated `schemas/.../1.json`).

Generate the golden file from the exported schema, then **read it against the port list** (21 Echo tables, `PodcastEntity`, `ArtistPageCache`, `track_match`, plus the 3 views); do not accept it unread:
```bash
jq -r '.database | ((.entities[].tableName | "table:" + .), ((.views // [])[].viewName | "view:" + .))' \
  data/schemas/app.aria.data.AriaDatabase/1.json | sort > data/expected-schema.txt
cat data/expected-schema.txt
```
If the counts are not 24 and 3, check `grep -l "^@Entity" data/src/main/kotlin/app/aria/data/entities/*.kt` before changing the test; adjust the numbers only if an entity is legitimately absent from the port, and record why in the commit message. Re-run the test: PASS, 3 tests.

- [ ] **Step 5: Write the failing repository test**

`data/src/test/kotlin/app/aria/data/PlayEventRepositoryTest.kt`:
```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.aria.domain.Track
import app.aria.domain.TrackId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlayEventRepositoryTest {
    private lateinit var db: AriaDatabase
    private lateinit var repo: PlayEventRepositoryImpl
    private val track = Track(TrackId.youtube("dQw4w9WgXcQ"), "Song", listOf("Artist"), 213_000L, null)

    @Before fun open() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AriaDatabase::class.java).allowMainThreadQueries().build()
        repo = PlayEventRepositoryImpl(db.playEventDao())
    }

    @After fun close() = db.close()

    @Test fun unknownTrackCountsZero() = runBlocking {
        assertEquals(0, repo.playCount(track.id))
    }

    @Test fun recordInsertsSongAndEvents() = runBlocking {
        repo.record(track, playedAtEpochMs = 1_000L, playTimeMs = 30_000L)
        repo.record(track, playedAtEpochMs = 2_000L, playTimeMs = 45_000L)
        assertEquals(2, repo.playCount(track.id))
    }
}
```

- [ ] **Step 6: Run to verify failure**

Run: `./gradlew :data:testDebugUnitTest --tests '*PlayEventRepositoryTest'`
Expected: FAIL, unresolved `PlayEventRepositoryImpl` / `playEventDao`.

- [ ] **Step 7: Implement DAO and repository**

`data/src/main/kotlin/app/aria/data/PlayEventDao.kt`:
```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import app.aria.data.entities.Event
import app.aria.data.entities.SongEntity

@Dao
abstract class PlayEventDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertSong(song: SongEntity)

    @Insert
    protected abstract suspend fun insertEvent(event: Event)

    @Query("SELECT COUNT(*) FROM event WHERE songId = :songId")
    abstract suspend fun countEvents(songId: String): Int

    @Transaction
    open suspend fun record(song: SongEntity, event: Event) {
        insertSong(song)
        insertEvent(event)
    }
}
```

`data/src/main/kotlin/app/aria/data/PlayEventRepositoryImpl.kt`:
```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data

import app.aria.data.entities.Event
import app.aria.data.entities.SongEntity
import app.aria.domain.PlayEventRepository
import app.aria.domain.Track
import app.aria.domain.TrackId
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

class PlayEventRepositoryImpl(private val dao: PlayEventDao) : PlayEventRepository {
    override suspend fun record(track: Track, playedAtEpochMs: Long, playTimeMs: Long) {
        val song = SongEntity(
            id = track.id.raw,
            title = track.title,
            duration = track.durationMs?.let { (it / 1000).toInt() } ?: -1,
            thumbnailUrl = track.thumbnailUrl,
        )
        val at = LocalDateTime.ofInstant(Instant.ofEpochMilli(playedAtEpochMs), ZoneOffset.UTC)
        dao.record(song, Event(songId = track.id.raw, timestamp = at, playTime = playTimeMs))
    }

    override suspend fun playCount(id: TrackId): Int = dao.countEvents(id.raw)
}
```

- [ ] **Step 8: Run to verify pass, check ledger, commit**

Run: `./gradlew :data:testDebugUnitTest`
Expected: PASS (all data tests). `git status` shows an updated `data/schemas/.../1.json`.

```bash
git add -A && git commit -m "feat(data): port lineage entities and add PlayEventRepository (provenance recorded)"
```

---

### Task 6: `:core` signed registry verification

Implements the spec's registry security: strict schema, Ed25519 signature against embedded keys, max age, last-known-good fallback. Data only, never code.

**Files:**
- Create: `core/build.gradle.kts`, `core/src/main/kotlin/app/aria/core/registry/Registry.kt`, `core/src/main/kotlin/app/aria/core/registry/RegistryVerifier.kt`, `core/src/main/kotlin/app/aria/core/registry/RegistryRepository.kt`, `core/src/test/kotlin/app/aria/core/registry/RegistryVerifierTest.kt`, `core/src/test/kotlin/app/aria/core/registry/RegistryRepositoryTest.kt`

**Interfaces:**
- Produces:
  - `@Serializable data class Registry(issuedAtEpochMs: Long, operations: Map<String, HashEntry>, totpSecret: String?, killSwitches: Map<String, Boolean>, youtubeClients: Map<String, String>)`; `@Serializable data class HashEntry(hash: String, previousHash: String?)`
  - `class RegistryVerifier(publicKeys: List<ByteArray>, allowedOperations: Set<String>, maxAgeMs: Long, maxFutureSkewMs: Long = 300_000L, clock: () -> Long)` with `fun verify(payload: ByteArray, signature: ByteArray): VerifyResult` and `companion fun signingInput(payload: ByteArray): ByteArray` (the exact bytes that are signed: the context prefix `aria-registry-v1\n` followed by the payload; CI signing tools and tests must use it)
  - `sealed interface VerifyResult { data class Valid(val registry: Registry); data class Invalid(val reason: String) }`
  - `interface RegistryStore { fun read(): ByteArray?; fun write(bytes: ByteArray) }`
  - `class RegistryRepository(verifier, store, embeddedFallback: Registry?)` with `fun accept(payload: ByteArray, signature: ByteArray): VerifyResult` (also rejects a registry older than the current one) and `fun current(): Registry?`

**Acceptance Criteria:**
- A correctly signed, in-schema, fresh payload is `Valid`. A payload signed by an unknown key, signed without the context prefix, with a tampered byte, older than `maxAgeMs`, dated more than `maxFutureSkewMs` in the future, over 64 KiB, or with a hash that is not 64 lowercase hex characters, an operation name outside the allowlist, or a `youtubeClients` value outside `^[A-Za-z0-9_.-]{1,64}$` is `Invalid` with a reason.
- Either of two embedded keys verifies (key rotation).
- `RegistryRepository.accept` stores only `Valid` payloads; after an `Invalid` result `current()` still returns the previously accepted registry; with nothing stored it returns `embeddedFallback`.
- Anti-rollback: a valid registry with `issuedAtEpochMs` lower than the currently held one (stored, or the embedded fallback when nothing is stored) is rejected; an equal timestamp is accepted.
- The registry is signed, not encrypted, and is published on a public host: it must never contain anything confidential. (`totpSecret` is already public in the community source it is taken from.)

- [ ] **Step 1: Write `core/build.gradle.kts`**

```kotlin
plugins {
    id("com.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.aria.core"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

kotlin { jvmToolchain(21) }

dependencies {
    api(project(":domain"))
    implementation(libs.tink.android)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
```
If `kotlinx-serialization-json` has no catalog alias, `grep -n serialization gradle/libs.versions.toml` and use the existing one.

- [ ] **Step 2: Write the failing verifier tests**

`core/src/test/kotlin/app/aria/core/registry/RegistryVerifierTest.kt`:
```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.core.registry

import com.google.crypto.tink.subtle.Ed25519Sign
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RegistryVerifierTest {
    private val now = 1_000_000_000L
    private val hash64 = "a".repeat(64)
    private val keyA = Ed25519Sign.KeyPair.newKeyPair()
    private val keyB = Ed25519Sign.KeyPair.newKeyPair()
    private val unknown = Ed25519Sign.KeyPair.newKeyPair()

    private fun verifier(maxAge: Long = 86_400_000L) = RegistryVerifier(
        publicKeys = listOf(keyA.publicKey, keyB.publicKey),
        allowedOperations = setOf("fetchPlaylist", "searchDesktop"),
        maxAgeMs = maxAge,
        clock = { now },
    )

    private fun json(
        issuedAt: Long = now,
        hash: String = hash64,
        op: String = "searchDesktop",
        client: String = "ANDROID_MUSIC",
    ) = """{"issuedAtEpochMs":$issuedAt,"operations":{"$op":{"hash":"$hash"}},"killSwitches":{"kugou":true},"youtubeClients":{"music":"$client"}}"""
        .toByteArray()

    private fun sign(payload: ByteArray, key: Ed25519Sign.KeyPair = keyA) =
        Ed25519Sign(key.privateKey).sign(RegistryVerifier.signingInput(payload))

    @Test fun validPayloadVerifies() {
        val p = json()
        val r = verifier().verify(p, sign(p))
        assertTrue(r is VerifyResult.Valid)
        assertEquals(true, (r as VerifyResult.Valid).registry.killSwitches["kugou"])
    }

    @Test fun secondEmbeddedKeyAlsoVerifies() {
        val p = json()
        assertTrue(verifier().verify(p, sign(p, keyB)) is VerifyResult.Valid)
    }

    @Test fun unknownKeyIsRejected() {
        val p = json()
        assertTrue(verifier().verify(p, sign(p, unknown)) is VerifyResult.Invalid)
    }

    @Test fun tamperedPayloadIsRejected() {
        val p = json()
        val sig = sign(p)
        p[p.size - 3] = (p[p.size - 3] + 1).toByte()
        assertTrue(verifier().verify(p, sig) is VerifyResult.Invalid)
    }

    @Test fun signatureOverBarePayloadWithoutContextIsRejected() {
        val p = json()
        val bare = Ed25519Sign(keyA.privateKey).sign(p)
        assertTrue(verifier().verify(p, bare) is VerifyResult.Invalid)
    }

    @Test fun farFutureRegistryIsRejected() {
        val p = json(issuedAt = now + 10 * 60_000L)
        assertTrue(verifier().verify(p, sign(p)) is VerifyResult.Invalid)
    }

    @Test fun slightlyFutureRegistryWithinSkewIsAccepted() {
        val p = json(issuedAt = now + 60_000L)
        assertTrue(verifier().verify(p, sign(p)) is VerifyResult.Valid)
    }

    @Test fun staleRegistryIsRejected() {
        val p = json(issuedAt = now - 90_000_000L)
        assertTrue(verifier(maxAge = 86_400_000L).verify(p, sign(p)) is VerifyResult.Invalid)
    }

    @Test fun badHashIsRejected() {
        val p = json(hash = "XYZ")
        assertTrue(verifier().verify(p, sign(p)) is VerifyResult.Invalid)
        val upper = json(hash = "A".repeat(64))
        assertTrue(verifier().verify(upper, sign(upper)) is VerifyResult.Invalid)
    }

    @Test fun operationOutsideAllowlistIsRejected() {
        val p = json(op = "somethingElse")
        assertTrue(verifier().verify(p, sign(p)) is VerifyResult.Invalid)
    }

    @Test fun clientConfigMustBePlainData() {
        val p = json(client = "a; rm -rf /")
        assertTrue(verifier().verify(p, sign(p)) is VerifyResult.Invalid)
    }

    @Test fun oversizedPayloadIsRejected() {
        val big = ByteArray(65 * 1024) { 'x'.code.toByte() }
        assertTrue(verifier().verify(big, sign(big)) is VerifyResult.Invalid)
    }

    @Test fun malformedJsonIsRejected() {
        val p = "not json".toByteArray()
        assertTrue(verifier().verify(p, sign(p)) is VerifyResult.Invalid)
    }
}
```

- [ ] **Step 3: Run to verify failure**

Run: `./gradlew :core:testDebugUnitTest --tests '*RegistryVerifierTest'`
Expected: FAIL, unresolved `RegistryVerifier`.

- [ ] **Step 4: Implement model and verifier**

`core/src/main/kotlin/app/aria/core/registry/Registry.kt`:
```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.core.registry

import kotlinx.serialization.Serializable

@Serializable
data class HashEntry(val hash: String, val previousHash: String? = null)

/** Data only. The registry never carries executable code. */
@Serializable
data class Registry(
    val issuedAtEpochMs: Long,
    val operations: Map<String, HashEntry> = emptyMap(),
    val totpSecret: String? = null,
    val killSwitches: Map<String, Boolean> = emptyMap(),
    val youtubeClients: Map<String, String> = emptyMap(),
)

sealed interface VerifyResult {
    data class Valid(val registry: Registry) : VerifyResult
    data class Invalid(val reason: String) : VerifyResult
}
```

`core/src/main/kotlin/app/aria/core/registry/RegistryVerifier.kt`:
```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.core.registry

import com.google.crypto.tink.subtle.Ed25519Verify
import java.security.GeneralSecurityException
import kotlinx.serialization.json.Json

class RegistryVerifier(
    private val publicKeys: List<ByteArray>,
    private val allowedOperations: Set<String>,
    private val maxAgeMs: Long,
    private val maxFutureSkewMs: Long = 300_000L,
    private val clock: () -> Long,
) {
    private val json = Json { ignoreUnknownKeys = false }

    fun verify(payload: ByteArray, signature: ByteArray): VerifyResult {
        if (payload.size > MAX_BYTES) return VerifyResult.Invalid("payload larger than $MAX_BYTES bytes")
        if (!signedByAnyKey(payload, signature)) return VerifyResult.Invalid("signature does not match any embedded key")
        val registry = try {
            json.decodeFromString<Registry>(payload.decodeToString())
        } catch (e: Exception) {
            return VerifyResult.Invalid("malformed registry: ${e.javaClass.simpleName}")
        }
        val age = clock() - registry.issuedAtEpochMs
        if (age < -maxFutureSkewMs) return VerifyResult.Invalid("registry is dated ${-age} ms in the future, limit $maxFutureSkewMs")
        if (age > maxAgeMs) return VerifyResult.Invalid("registry is $age ms old, limit $maxAgeMs")
        registry.operations.forEach { (name, entry) ->
            if (name !in allowedOperations) return VerifyResult.Invalid("operation not allowed: $name")
            if (!HASH.matches(entry.hash)) return VerifyResult.Invalid("bad hash for $name")
            if (entry.previousHash != null && !HASH.matches(entry.previousHash)) {
                return VerifyResult.Invalid("bad previous hash for $name")
            }
        }
        registry.youtubeClients.forEach { (key, value) ->
            if (!CLIENT_VALUE.matches(value)) return VerifyResult.Invalid("bad client config value for $key")
        }
        return VerifyResult.Valid(registry)
    }

    private fun signedByAnyKey(payload: ByteArray, signature: ByteArray): Boolean =
        publicKeys.any { key ->
            try {
                Ed25519Verify(key).verify(signature, signingInput(payload))
                true
            } catch (_: GeneralSecurityException) {
                false
            }
        }

    companion object {
        /** The registry is signed, not encrypted, and published on a public host: never put secrets in it. */
        private val CONTEXT = "aria-registry-v1\n".toByteArray()

        /** The exact bytes that are signed. CI signing tools must use this same function. */
        fun signingInput(payload: ByteArray): ByteArray = CONTEXT + payload

        private const val MAX_BYTES = 64 * 1024
        private val HASH = Regex("^[0-9a-f]{64}$")
        private val CLIENT_VALUE = Regex("^[A-Za-z0-9_.-]{1,64}$")
    }
}
```

- [ ] **Step 5: Run to verify pass**

Run: `./gradlew :core:testDebugUnitTest --tests '*RegistryVerifierTest'`
Expected: PASS, 13 tests.

- [ ] **Step 6: Write the failing repository tests**

`core/src/test/kotlin/app/aria/core/registry/RegistryRepositoryTest.kt`:
```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.core.registry

import com.google.crypto.tink.subtle.Ed25519Sign
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RegistryRepositoryTest {
    private val now = 1_000L
    private val key = Ed25519Sign.KeyPair.newKeyPair()
    private val verifier = RegistryVerifier(listOf(key.publicKey), setOf("searchDesktop"), 10_000L) { now }

    private class MemStore : RegistryStore {
        var bytes: ByteArray? = null
        override fun read() = bytes
        override fun write(bytes: ByteArray) { this.bytes = bytes }
    }

    private fun payload(issuedAt: Long, killKugou: Boolean) =
        """{"issuedAtEpochMs":$issuedAt,"killSwitches":{"kugou":$killKugou}}""".toByteArray()

    private fun sig(p: ByteArray) = Ed25519Sign(key.privateKey).sign(RegistryVerifier.signingInput(p))

    @Test fun validPayloadIsStoredAndCurrent() {
        val repo = RegistryRepository(verifier, MemStore(), embeddedFallback = null)
        val p = payload(now, killKugou = true)
        assertTrue(repo.accept(p, sig(p)) is VerifyResult.Valid)
        assertEquals(true, repo.current()?.killSwitches?.get("kugou"))
    }

    @Test fun invalidPayloadKeepsLastKnownGood() {
        val repo = RegistryRepository(verifier, MemStore(), embeddedFallback = null)
        val good = payload(now, killKugou = true)
        repo.accept(good, sig(good))
        val bad = payload(now, killKugou = false)
        assertTrue(repo.accept(bad, ByteArray(64)) is VerifyResult.Invalid)
        assertEquals(true, repo.current()?.killSwitches?.get("kugou"))
    }

    @Test fun olderRegistryThanStoredIsRejectedAsRollback() {
        val repo = RegistryRepository(verifier, MemStore(), embeddedFallback = null)
        val newer = payload(now, killKugou = true)
        repo.accept(newer, sig(newer))
        val older = payload(now - 5_000L, killKugou = false)
        assertTrue(repo.accept(older, sig(older)) is VerifyResult.Invalid)
        assertEquals(true, repo.current()?.killSwitches?.get("kugou"))
    }

    @Test fun equalTimestampIsAccepted() {
        val repo = RegistryRepository(verifier, MemStore(), embeddedFallback = null)
        val p = payload(now, killKugou = true)
        repo.accept(p, sig(p))
        assertTrue(repo.accept(p, sig(p)) is VerifyResult.Valid)
    }

    @Test fun registryOlderThanEmbeddedFallbackIsRejected() {
        val embedded = Registry(issuedAtEpochMs = now + 100L, killSwitches = mapOf("kugou" to true))
        val repo = RegistryRepository(verifier, MemStore(), embedded)
        val older = payload(now, killKugou = false)
        assertTrue(repo.accept(older, sig(older)) is VerifyResult.Invalid)
        assertEquals(embedded, repo.current())
    }

    @Test fun nothingStoredFallsBackToEmbedded() {
        val embedded = Registry(issuedAtEpochMs = 1L, killSwitches = mapOf("kugou" to false))
        val repo = RegistryRepository(verifier, MemStore(), embedded)
        assertEquals(embedded, repo.current())
    }

    @Test fun nothingStoredAndNoFallbackIsNull() {
        assertNull(RegistryRepository(verifier, MemStore(), null).current())
    }
}
```

- [ ] **Step 7: Run to verify failure, then implement**

Run: `./gradlew :core:testDebugUnitTest --tests '*RegistryRepositoryTest'`
Expected: FAIL, unresolved `RegistryRepository`.

`core/src/main/kotlin/app/aria/core/registry/RegistryRepository.kt`:
```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.core.registry

import kotlinx.serialization.json.Json

interface RegistryStore {
    fun read(): ByteArray?
    fun write(bytes: ByteArray)
}

/** Keeps the last registry that passed verification; invalid input never replaces it. */
class RegistryRepository(
    private val verifier: RegistryVerifier,
    private val store: RegistryStore,
    private val embeddedFallback: Registry?,
) {
    private val json = Json { ignoreUnknownKeys = false }

    fun accept(payload: ByteArray, signature: ByteArray): VerifyResult {
        val result = verifier.verify(payload, signature)
        if (result !is VerifyResult.Valid) return result
        // Anti-rollback: a replayed older registry could re-enable a killed provider or restore a rotated hash.
        val floor = current()?.issuedAtEpochMs
        if (floor != null && result.registry.issuedAtEpochMs < floor) {
            return VerifyResult.Invalid("registry is older than the current one (rollback)")
        }
        store.write(payload)
        return result
    }

    /** Stored payloads were verified when accepted; they are re-parsed, not re-trusted blindly on age. */
    fun current(): Registry? {
        val stored = store.read()?.let { runCatching { json.decodeFromString<Registry>(it.decodeToString()) }.getOrNull() }
        return stored ?: embeddedFallback
    }
}
```

- [ ] **Step 8: Run to verify pass and commit**

Run: `./gradlew :core:testDebugUnitTest`
Expected: PASS, 20 tests.

```bash
git add -A && git commit -m "feat(core): add signed registry verification with last-known-good fallback"
```

---

### Task 7: `:designsystem` tokens and `GlassSurface` with fallback

**Files:**
- Create: `designsystem/build.gradle.kts`, `designsystem/src/main/kotlin/app/aria/designsystem/GlassMode.kt`, `designsystem/src/main/kotlin/app/aria/designsystem/GlassSurface.kt`, `designsystem/src/main/kotlin/app/aria/designsystem/Tokens.kt`, `designsystem/src/test/kotlin/app/aria/designsystem/GlassResolverTest.kt`, `designsystem/src/androidTest/kotlin/app/aria/designsystem/GlassSurfaceTest.kt`, `licenses/Apache-2.0.txt`, ported `designsystem/src/main/kotlin/app/aria/designsystem/backdrop/*` (30 files vendored from Kyant0/backdrop via Echo) and `GlassEffect.kt` (Echo's own wrapper)
- Modify: `docs/provenance.csv`, `CREDITS.md`

**Interfaces:**
- Produces:
  - `enum class GlassMode { AUTO, ON, OFF }`
  - `object GlassResolver { fun isSupported(sdkInt: Int): Boolean; fun resolve(mode: GlassMode, sdkInt: Int, batterySaver: Boolean): Boolean }`
  - `@Composable fun GlassSurface(modifier: Modifier, mode: GlassMode, shape: Shape, content: @Composable () -> Unit)`
  - `object AriaTokens` with named colors/alphas (`glassFallbackAlpha`, `scrimTop`, `scrimBottom`).

**Acceptance Criteria:**
- `resolve` returns `false` below API 31 in every mode, including `ON`; `AUTO` is `false` under battery saver; `OFF` is always `false`; `ON` is `true` on API 31+ regardless of battery saver.
- On API 26–30 `GlassSurface` renders an opaque-enough translucent `Surface` with the same shape, using `AriaTokens.glassFallbackAlpha`, and the glass code path is never invoked.
- The backdrop code is vendored from Kyant0/backdrop v2.0.0 (Apache-2.0, via Echo, which added a resolution scale), not replaced by the published artifact (stress-test branch 5). All 30 vendored files keep their original header, gain a `// Modified for Aria: package renamed.` line, and `licenses/Apache-2.0.txt` ships in the repo. `CREDITS.md` credits Kyant0/backdrop. The ledger has two rows: `backdrop` (Apache-2.0) and `GlassEffect.kt` (GPL-3.0).
- Echo's logo, banner and seed color are not used.

- [ ] **Step 1: Write `designsystem/build.gradle.kts`**

```kotlin
plugins {
    id("com.android.library")
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "app.aria.designsystem"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
    buildFeatures { compose = true }
}

kotlin { jvmToolchain(21) }

dependencies {
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.runtime)
    implementation(libs.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.compose.ui.test.junit4)
}
```
Add catalog aliases `compose-ui-test-junit4` (module `androidx.compose.ui:ui-test-junit4`, `version.ref = "compose"`) if absent.

- [ ] **Step 2: Write the failing resolver test**

`designsystem/src/test/kotlin/app/aria/designsystem/GlassResolverTest.kt`:
```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.designsystem

import org.junit.Assert.assertEquals
import org.junit.Test

class GlassResolverTest {
    @Test fun neverEnabledBelowApi31() {
        GlassMode.entries.forEach { mode ->
            listOf(26, 30).forEach { sdk -> assertEquals("$mode@$sdk", false, GlassResolver.resolve(mode, sdk, false)) }
        }
    }

    @Test fun onIsEnabledOnApi31Plus() {
        assertEquals(true, GlassResolver.resolve(GlassMode.ON, 31, batterySaver = true))
        assertEquals(true, GlassResolver.resolve(GlassMode.ON, 36, batterySaver = false))
    }

    @Test fun autoFollowsSupportAndBatterySaver() {
        assertEquals(true, GlassResolver.resolve(GlassMode.AUTO, 33, batterySaver = false))
        assertEquals(false, GlassResolver.resolve(GlassMode.AUTO, 33, batterySaver = true))
    }

    @Test fun offIsAlwaysOff() {
        assertEquals(false, GlassResolver.resolve(GlassMode.OFF, 36, batterySaver = false))
    }

    @Test fun supportThresholdIsApi31() {
        assertEquals(false, GlassResolver.isSupported(30))
        assertEquals(true, GlassResolver.isSupported(31))
    }
}
```

- [ ] **Step 3: Run to verify failure**

Run: `./gradlew :designsystem:testDebugUnitTest`
Expected: FAIL, unresolved `GlassResolver`.

- [ ] **Step 4: Implement the resolver and tokens**

`designsystem/src/main/kotlin/app/aria/designsystem/GlassMode.kt`:
```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.designsystem

enum class GlassMode { AUTO, ON, OFF }

object GlassResolver {
    private const val API_S = 31

    /** The backdrop blur pipeline needs RenderEffect (Android 12, API 31). */
    fun isSupported(sdkInt: Int): Boolean = sdkInt >= API_S

    fun resolve(mode: GlassMode, sdkInt: Int, batterySaver: Boolean): Boolean {
        if (!isSupported(sdkInt)) return false
        return when (mode) {
            GlassMode.OFF -> false
            GlassMode.ON -> true
            GlassMode.AUTO -> !batterySaver
        }
    }
}
```

`designsystem/src/main/kotlin/app/aria/designsystem/Tokens.kt`:
```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.designsystem

/** Named tokens replace literals such as surfaceVariant.copy(alpha = 0.3f). */
object AriaTokens {
    const val glassFallbackAlpha = 0.82f
    const val scrimTop = 0.45f
    const val scrimBottom = 0.80f
}
```

- [ ] **Step 5: Run to verify pass**

Run: `./gradlew :designsystem:testDebugUnitTest`
Expected: PASS, 5 tests.

- [ ] **Step 6: Audit and port the backdrop code (Apache-2.0 vendored code)**

Audit (read-only). At the pinned Echo commit, 30 of the 31 files carry the header `Vendored from Kyant0/backdrop v2.0.0 (io.github.kyant0:backdrop) ... Copyright 2025 Kyant0, Apache License 2.0`, and the 31st (`GlassEffect.kt`) is Echo's own wrapper under GPL-3.0. Confirm that still holds:
```bash
E=/home/claude/echomusicapp/echo-music/app/src/main/kotlin/com/music/echo/ui/component
find $E/backdrop $E/GlassEffect.kt -name '*.kt' | wc -l
find $E/backdrop -name '*.kt' | xargs grep -L "Vendored from Kyant0/backdrop v2.0.0" || true
find $E/backdrop $E/GlassEffect.kt -name '*.kt' | xargs grep -h -o -i -E "Apache License 2.0|GPL-3.0" | sort | uniq -c
```
Expected: `31`; the second command lists nothing; the third shows 30 `Apache License 2.0` and 1 `GPL-3.0`. Any other license name, or a file with no header, stops the port: ask the maintainer.

Why vendor instead of depending on `io.github.kyant0:backdrop`: Echo's change adds a backdrop resolution scale (the cheaper-rendering feature this design needs) that upstream lacks, and Echo's header records that the binary AARs broke at runtime against its Compose version. Aria is on a newer Compose, so that risk is not assumed away.

Port, with the Apache-2.0 obligations:
```bash
D=/home/claude/aria/designsystem/src/main/kotlin/app/aria/designsystem
mkdir -p $D/backdrop && cp -r $E/backdrop/. $D/backdrop/ && cp $E/GlassEffect.kt $D/GlassEffect.kt
# find, not a glob: the backdrop tree has nested directories (effects, shadow, highlight, backdrops, internal)
find $D -name '*.kt' | xargs sed -i 's/echo\.music\.iad1tya\.ui\.component/app.aria.designsystem/g; s/com\.music\.echo\.ui\.component/app.aria.designsystem/g'
# Apache-2.0 section 4(b): modified files carry a notice that they were changed
for f in $(grep -rl "Vendored from Kyant0/backdrop" $D); do
  grep -q "Modified for Aria" "$f" || sed -i 's|^package |// Modified for Aria: package renamed.\npackage |' "$f"
done
# SPDX header only where no original header exists (GlassEffect.kt keeps Echo's GPL-3.0 header)
for f in $(find $D -name '*.kt'); do grep -q -E "SPDX-License-Identifier|Licensed under|Vendored from|Copyright" "$f" || sed -i '1i // SPDX-License-Identifier: GPL-3.0-only' "$f"; done
# Apache-2.0 section 4(a): ship a copy of the license
cd /home/claude/aria && mkdir -p licenses && cp /usr/share/common-licenses/Apache-2.0 licenses/Apache-2.0.txt
head -3 licenses/Apache-2.0.txt
grep -rL "Modified for Aria" $(grep -rl "Vendored from" $D) || true
chmod +x scripts/port-scan.sh && scripts/port-scan.sh designsystem/src/main/kotlin
```
Expected: the `head` shows `Apache License` and `Version 2.0, January 2004` (if the system file is absent, download the text from apache.org/licenses/LICENSE-2.0.txt and check the same two lines); the `grep -rL` prints nothing; the scan prints `distinct foreign imports: 0 (limit 10)` (a dry run at the pinned commit gave 0 after the rename). The only non-platform import left is `org.intellij.lang.annotations.Language`, which comes with the Kotlin toolchain's annotations dependency; add `compileOnly("org.jetbrains:annotations:26.0.2")` to `designsystem/build.gradle.kts` only if the compiler cannot resolve it.

Upstream NOTICE: with network access, run `curl -sSL -o /dev/null -w '%{http_code}\n' https://raw.githubusercontent.com/Kyant0/backdrop/main/NOTICE`. `404` means no NOTICE file; `200` means copy its contents to `licenses/NOTICE`. Without network access, write "NOTICE not checked" in the ledger notes; `docs/release-gates.md` (Task 12) makes this check a gate before the first beta.

Ledger rows (append to `docs/provenance.csv`; the notes contain commas, so quote them):
```csv
designsystem/src/main/kotlin/app/aria/designsystem/backdrop,EchoMusicApp/Echo-Music,b04d7507b33c0671b3a23d2658b24a376add4cc7,Apache-2.0,adapted,"Vendored by Echo from Kyant0/backdrop v2.0.0 (Copyright 2025 Kyant0) with Echo changes (backdrop resolution scale; RoundedRectangularShape support dropped); package renamed again for Aria; 30 files"
designsystem/src/main/kotlin/app/aria/designsystem/GlassEffect.kt,EchoMusicApp/Echo-Music,b04d7507b33c0671b3a23d2658b24a376add4cc7,GPL-3.0,adapted,"Echo liquidGlass wrapper (vivimusic lineage header); package renamed"
```
Append to `CREDITS.md`:
```markdown
| [Kyant0/backdrop](https://github.com/Kyant0/backdrop) v2.0.0 (Apache-2.0) | Backdrop blur, lens and highlight effects (30 files in `designsystem/.../backdrop`) | Vendored by Echo Music and taken from there with Echo's changes (resolution scale added, shapes support removed); package renamed. Original copyright headers kept; license text in `licenses/Apache-2.0.txt` |
```
Then remove any dependency of the ported files on Echo-only types (for example `LocalGlassEffectConfig` consumers in settings) by reading the compiler errors; keep `Modifier.liquidGlass(config, shape, applyEdgeEffects)`, `LocalAppBackdrop`, and `isGlassSupported`.

- [ ] **Step 7: Implement `GlassSurface`**

`designsystem/src/main/kotlin/app/aria/designsystem/GlassSurface.kt`:
```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.designsystem

import android.os.Build
import android.os.PowerManager
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext

/**
 * Glass when the device and mode allow it, otherwise a translucent Surface of the same shape.
 * The fallback is a first-class design, not an error state.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    mode: GlassMode = GlassMode.AUTO,
    shape: Shape = MaterialTheme.shapes.large,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val batterySaver = (context.getSystemService(PowerManager::class.java))?.isPowerSaveMode == true
    val glass = GlassResolver.resolve(mode, Build.VERSION.SDK_INT, batterySaver)
    if (glass) {
        Box(modifier = modifier.liquidGlass(config = LocalGlassEffectConfig.current, shape = shape, applyEdgeEffects = true)) {
            content()
        }
    } else {
        Surface(
            modifier = modifier,
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = AriaTokens.glassFallbackAlpha),
            content = content,
        )
    }
}
```

- [ ] **Step 8: Add the instrumented fallback test**

`designsystem/src/androidTest/kotlin/app/aria/designsystem/GlassSurfaceTest.kt`:
```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.designsystem

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class GlassSurfaceTest {
    @get:Rule val rule = createComposeRule()

    @Test fun contentIsShownWhenGlassIsOff() {
        rule.setContent { GlassSurface(mode = GlassMode.OFF) { Text("hello") } }
        rule.onNodeWithText("hello").assertIsDisplayed()
    }

    @Test fun contentIsShownWhenGlassIsRequested() {
        rule.setContent { GlassSurface(mode = GlassMode.ON) { Text("hello") } }
        rule.onNodeWithText("hello").assertIsDisplayed()
    }
}
```
CI runs this on emulators at API 26 and API 34 (Task 10) so both the fallback and glass paths are exercised.

- [ ] **Step 9: Verify and commit**

Run: `./gradlew :designsystem:testDebugUnitTest :designsystem:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

```bash
git add -A && git commit -m "feat(designsystem): add GlassSurface with first-class fallback; port Echo backdrop (provenance recorded)"
```

---

### Task 8: `:innertube` port and the `SearchSource` / `StreamSource` adapters

Depends on Task 0 (license and store findings). **Do not start this task if Task 0 found InnerTubeX's license incompatible or unknown.**

**Files:**
- Create: `innertube/build.gradle.kts`, `innertube/src/main/kotlin/app/aria/innertube/**` (ported), `innertube/src/main/kotlin/app/aria/innertube/adapter/YouTubeSearchSource.kt`, `innertube/src/main/kotlin/app/aria/innertube/adapter/YouTubeStreamSource.kt`, `innertube/src/main/kotlin/app/aria/innertube/adapter/Mapping.kt`, `innertube/src/test/kotlin/app/aria/innertube/adapter/MappingTest.kt`
- Modify: `docs/provenance.csv`

**Interfaces:**
- Consumes: `Track`, `TrackId`, `ResolvedStream`, `StreamUnavailableException`, `SearchSource`, `StreamSource` (Task 2).
- Produces: `class YouTubeSearchSource : SearchSource`, `class YouTubeStreamSource : StreamSource`, `fun SongItem.toTrack(): Track`, `fun YouTube... PlayerResponse.StreamingData.bestAudio(): Format?`.

**Acceptance Criteria:**
- Ported module compiles under the new package `app.aria.innertube`; the library import `com.metrolist.innertubex` is unchanged.
- `SongItem.toTrack()` maps id → `TrackId.youtube`, artist names, `duration` seconds → milliseconds, thumbnail.
- `bestAudio()` picks the highest-bitrate audio format that has a non-null `url` and returns null when none does; `resolve()` then fails with `StreamUnavailableException`.
- Ledger row recorded for the module (origin Meld, commit `fe9a666227ff3d6667075e65a4330518a724524b`).

- [ ] **Step 1: Port Meld's module with a package rename**

```bash
M=/home/claude/francescograzioso/meld/innertube
D=/home/claude/aria/innertube
mkdir -p $D/src/main/kotlin/app/aria/innertube
cp -r $M/src/main/kotlin/com/metrolist/innertube/. $D/src/main/kotlin/app/aria/innertube/
cp $M/build.gradle.kts $D/build.gradle.kts
# \b keeps com.metrolist.innertubex (the library) untouched
grep -rl "com\.metrolist\.innertube\b" $D | xargs sed -i 's/com\.metrolist\.innertube\b/app.aria.innertube/g'
sed -i 's/namespace = "com.metrolist.innertube"/namespace = "app.aria.innertube"/' $D/build.gradle.kts
grep -rn "com.metrolist.innertubex" $D | wc -l
grep -rn "com\.metrolist\.innertube[^x]" $D | head -3
cd /home/claude/aria && scripts/port-scan.sh innertube/src/main/kotlin
```
Expected: the first count is non-zero (library imports kept); the second command prints nothing; the scan prints `distinct foreign imports: 0 (limit 10)` (a dry run against Meld at the pinned commit gave 0). More than 10 means stop and ask the maintainer.

Then edit `innertube/build.gradle.kts`: Meld's file is already in the explicit template form, so keep its plugins, `android` and `kotlin` blocks; change only the namespace (done by the sed above), add `testOptions { unitTests.isIncludeAndroidResources = true }`, and add `api(project(":domain"))` to `dependencies`. Add `include(":innertube")` to `settings.gradle.kts`.

- [ ] **Step 2: Add SPDX headers and the ledger row**

```bash
for f in $(find $D/src -name '*.kt'); do grep -q -E "SPDX-License-Identifier|Licensed under" "$f" || sed -i '1i // SPDX-License-Identifier: GPL-3.0-only' "$f"; done
printf 'innertube,FrancescoGrazioso/Meld,fe9a666227ff3d6667075e65a4330518a724524b,GPL-3.0,adapted,"Ported module (about 9,700 lines); package renamed; wraps InnerTubeX; parsing remains in-tree"\n' >> /home/claude/aria/docs/provenance.csv
```

- [ ] **Step 3: Compile the port**

Run: `./gradlew :innertube:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`. Fix unresolved references by reading the errors; anything that referenced Metrolist app code outside `innertube/` is removed or stubbed behind the nearest `:domain` interface.

- [ ] **Step 4: Write the failing mapping tests**

`innertube/src/test/kotlin/app/aria/innertube/adapter/MappingTest.kt`:
```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.innertube.adapter

import app.aria.domain.TrackId
import app.aria.innertube.models.Artist
import app.aria.innertube.models.SongItem
import app.aria.innertube.models.response.PlayerResponse.StreamingData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MappingTest {
    private fun format(itag: Int, bitrate: Int, url: String?, audio: Boolean = true) = StreamingData.Format(
        itag = itag, url = url, mimeType = "audio/webm; codecs=\"opus\"", bitrate = bitrate,
        width = if (audio) null else 1920, height = if (audio) null else 1080,
        contentLength = 1_000L, quality = "tiny", fps = null, qualityLabel = null, averageBitrate = null,
        audioQuality = "AUDIO_QUALITY_MEDIUM", approxDurationMs = null, audioSampleRate = null,
        audioChannels = null, loudnessDb = null, lastModified = null, signatureCipher = null, cipher = null,
        audioTrack = null,
    )

    @Test fun songItemMapsToTrack() {
        val item = SongItem(
            id = "dQw4w9WgXcQ", title = "Song", artists = listOf(Artist("A", "id1"), Artist("B", null)),
            duration = 213, thumbnail = "https://example.invalid/t.jpg",
        )
        val t = item.toTrack()
        assertEquals(TrackId.youtube("dQw4w9WgXcQ"), t.id)
        assertEquals(listOf("A", "B"), t.artists)
        assertEquals(213_000L, t.durationMs)
        assertEquals("https://example.invalid/t.jpg", t.thumbnailUrl)
    }

    @Test fun missingDurationStaysNull() {
        val item = SongItem(id = "dQw4w9WgXcQ", title = "S", artists = emptyList(), thumbnail = "x")
        assertNull(item.toTrack().durationMs)
    }

    @Test fun bestAudioPicksHighestBitrateWithUrl() {
        val data = StreamingData(
            formats = null, expiresInSeconds = 21_540,
            adaptiveFormats = listOf(
                format(1, 50_000, "https://a"), format(2, 160_000, "https://b"),
                format(3, 320_000, null), format(4, 900_000, "https://video", audio = false),
            ),
        )
        assertEquals(2, data.bestAudio()?.itag)
    }

    @Test fun bestAudioIsNullWhenNoUrls() {
        val data = StreamingData(null, listOf(format(1, 50_000, null)), 21_540)
        assertNull(data.bestAudio())
    }
}
```
Run `grep -n "class SongItem" -A 20 innertube/src/main/kotlin/app/aria/innertube/models/YTItem.kt` first; the constructor above was read from Meld at the pinned commit, so if the port differs, change the test, not the intent.

- [ ] **Step 5: Run to verify failure**

Run: `./gradlew :innertube:testDebugUnitTest --tests '*MappingTest'`
Expected: FAIL, unresolved `toTrack` / `bestAudio`.

- [ ] **Step 6: Implement mapping and adapters**

`innertube/src/main/kotlin/app/aria/innertube/adapter/Mapping.kt`:
```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.innertube.adapter

import app.aria.domain.Track
import app.aria.domain.TrackId
import app.aria.innertube.models.SongItem
import app.aria.innertube.models.response.PlayerResponse.StreamingData

fun SongItem.toTrack(): Track = Track(
    id = TrackId.youtube(id),
    title = title,
    artists = artists.map { it.name },
    durationMs = duration?.let { it * 1000L },
    thumbnailUrl = thumbnail,
)

/** Highest-bitrate audio-only format that already carries a direct URL. */
fun StreamingData.bestAudio(): StreamingData.Format? =
    adaptiveFormats.filter { it.isAudio && it.url != null }.maxByOrNull { it.bitrate }
```

`innertube/src/main/kotlin/app/aria/innertube/adapter/YouTubeSearchSource.kt`:
```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.innertube.adapter

import app.aria.domain.SearchSource
import app.aria.domain.Track
import app.aria.innertube.YouTube
import app.aria.innertube.models.SongItem

class YouTubeSearchSource : SearchSource {
    override suspend fun searchTracks(query: String): Result<List<Track>> =
        YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).map { result ->
            result.items.filterIsInstance<SongItem>().map { it.toTrack() }
        }
}
```

`innertube/src/main/kotlin/app/aria/innertube/adapter/YouTubeStreamSource.kt`:
```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.innertube.adapter

import app.aria.domain.ResolvedStream
import app.aria.domain.StreamSource
import app.aria.domain.StreamUnavailableException
import app.aria.domain.TrackId
import app.aria.domain.TrackSource
import app.aria.innertube.YouTube
import com.metrolist.innertubex.models.YouTubeClient

/**
 * Skeleton resolver: asks the ANDROID_MUSIC client for the player response and takes a direct audio
 * URL. Formats that need signature deciphering or a PoToken are not handled here; they surface as
 * StreamUnavailableException and are covered later by the playback sub-project.
 */
class YouTubeStreamSource(private val clock: () -> Long = System::currentTimeMillis) : StreamSource {
    override suspend fun resolve(id: TrackId): Result<ResolvedStream> = runCatching {
        require(id.source == TrackSource.YOUTUBE) { "Not a YouTube track: $id" }
        val response = YouTube.player(id.value, client = YouTubeClient.ANDROID_MUSIC).getOrThrow()
        val data = response.streamingData ?: throw StreamUnavailableException("No streaming data for $id")
        val best = data.bestAudio() ?: throw StreamUnavailableException("No direct audio URL for $id")
        ResolvedStream(
            url = best.url!!,
            mimeType = best.mimeType,
            bitrate = best.bitrate,
            contentLength = best.contentLength,
            expiresAtEpochMs = clock() + data.expiresInSeconds * 1000L,
        )
    }
}
```
If `YouTube.player` is declared on a different receiver after the port, or `PlayerResponse.streamingData` has another name, run `grep -n "streamingData" innertube/src/main/kotlin/app/aria/innertube/models/response/PlayerResponse.kt` and adjust the line, not the interface.

- [ ] **Step 7: Run to verify pass and commit**

Run: `./gradlew :innertube:testDebugUnitTest`
Expected: PASS (mapping tests; any ported tests too).

```bash
git add -A && git commit -m "feat(innertube): port Meld module and add SearchSource/StreamSource adapters (provenance recorded)"
```

---

### Task 9: `:playback` minimal service and controller

Smallest Media3 foreground service that proves the boundary: depends only on `:domain`.

**Files:**
- Create: `playback/build.gradle.kts`, `playback/src/main/AndroidManifest.xml`, `playback/src/main/kotlin/app/aria/playback/AriaPlaybackService.kt`, `playback/src/main/kotlin/app/aria/playback/PlayerController.kt`, `playback/src/main/kotlin/app/aria/playback/ResumeStore.kt`, `playback/src/test/kotlin/app/aria/playback/PlayerControllerTest.kt`

**Interfaces:**
- Consumes: `StreamSource`, `ResolvedStream`, `PlayEventRepository`, `Track`, `TrackId` (Task 2).
- Produces:
  - `interface Player { fun play(url: String, startPositionMs: Long); fun positionMs(): Long; fun stop() }` (a seam so the controller is testable without Media3)
  - `interface ResumeStore { fun save(id: TrackId, positionMs: Long); fun load(id: TrackId): Long }`
  - `class PlayerController(streams: StreamSource, events: PlayEventRepository, player: Player, resume: ResumeStore, clock: () -> Long)` with `suspend fun start(track: Track): Result<Unit>` and `suspend fun stopAndRecord(track: Track)`
  - `class AriaPlaybackService : MediaSessionService`

**Acceptance Criteria:**
- `start` resolves the stream through `StreamSource`, starts the player at the saved resume position, and returns failure (without touching the player) if resolution fails.
- `stopAndRecord` saves the current position as the resume position and records one play event with the elapsed play time.
- `:playback` has no dependency on any project except `:domain` (checked by `checkModuleGraph`).

- [ ] **Step 1: Write `playback/build.gradle.kts`**

```kotlin
plugins { id("com.android.library") }

android {
    namespace = "app.aria.playback"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

kotlin { jvmToolchain(21) }

dependencies {
    api(project(":domain"))
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}
```
Run `grep -n "media3\|coroutines" gradle/libs.versions.toml` and use the existing alias names; add missing ones at the already-pinned version.

- [ ] **Step 2: Write the failing controller tests**

`playback/src/test/kotlin/app/aria/playback/PlayerControllerTest.kt`:
```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.playback

import app.aria.domain.PlayEventRepository
import app.aria.domain.ResolvedStream
import app.aria.domain.StreamSource
import app.aria.domain.StreamUnavailableException
import app.aria.domain.Track
import app.aria.domain.TrackId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerControllerTest {
    private val track = Track(TrackId.youtube("dQw4w9WgXcQ"), "Song", listOf("A"), 200_000L, null)

    private class FakePlayer : Player {
        var played: Pair<String, Long>? = null
        var position = 0L
        var stopped = false
        override fun play(url: String, startPositionMs: Long) { played = url to startPositionMs }
        override fun positionMs() = position
        override fun stop() { stopped = true }
    }

    private class FakeResume(var saved: Long = 0L) : ResumeStore {
        override fun save(id: TrackId, positionMs: Long) { saved = positionMs }
        override fun load(id: TrackId) = saved
    }

    private class FakeEvents : PlayEventRepository {
        val recorded = mutableListOf<Triple<Track, Long, Long>>()
        override suspend fun record(track: Track, playedAtEpochMs: Long, playTimeMs: Long) {
            recorded += Triple(track, playedAtEpochMs, playTimeMs)
        }
        override suspend fun playCount(id: TrackId) = recorded.size
    }

    private fun streams(result: Result<ResolvedStream>) = object : StreamSource {
        override suspend fun resolve(id: TrackId) = result
    }

    private val stream = ResolvedStream("https://example.invalid/a", "audio/webm", 160_000, 10L, null)

    @Test fun startPlaysAtSavedResumePosition() = runTest {
        val player = FakePlayer()
        val c = PlayerController(streams(Result.success(stream)), FakeEvents(), player, FakeResume(12_345L)) { 0L }
        assertTrue(c.start(track).isSuccess)
        assertEquals("https://example.invalid/a" to 12_345L, player.played)
    }

    @Test fun failedResolveDoesNotTouchPlayer() = runTest {
        val player = FakePlayer()
        val c = PlayerController(streams(Result.failure(StreamUnavailableException("no"))), FakeEvents(), player, FakeResume()) { 0L }
        assertTrue(c.start(track).isFailure)
        assertEquals(null, player.played)
    }

    @Test fun stopSavesPositionAndRecordsOneEvent() = runTest {
        val player = FakePlayer()
        val resume = FakeResume()
        val events = FakeEvents()
        var now = 1_000L
        val c = PlayerController(streams(Result.success(stream)), events, player, resume) { now }
        c.start(track)
        now = 31_000L
        player.position = 30_500L
        c.stopAndRecord(track)
        assertEquals(30_500L, resume.saved)
        assertEquals(1, events.recorded.size)
        assertEquals(30_000L, events.recorded.single().third)
        assertTrue(player.stopped)
    }
}
```

- [ ] **Step 3: Run to verify failure**

Run: `./gradlew :playback:testDebugUnitTest`
Expected: FAIL, unresolved `PlayerController`, `Player`, `ResumeStore`.

- [ ] **Step 4: Implement the controller**

`playback/src/main/kotlin/app/aria/playback/PlayerController.kt`:
```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.playback

import app.aria.domain.PlayEventRepository
import app.aria.domain.StreamSource
import app.aria.domain.Track

/** Seam over the real Media3 player so the controller is testable on the JVM. */
interface Player {
    fun play(url: String, startPositionMs: Long)
    fun positionMs(): Long
    fun stop()
}

interface ResumeStore {
    fun save(id: app.aria.domain.TrackId, positionMs: Long)
    fun load(id: app.aria.domain.TrackId): Long
}

class PlayerController(
    private val streams: StreamSource,
    private val events: PlayEventRepository,
    private val player: Player,
    private val resume: ResumeStore,
    private val clock: () -> Long,
) {
    private var startedAt: Long = 0L

    suspend fun start(track: Track): Result<Unit> =
        streams.resolve(track.id).map { stream ->
            player.play(stream.url, resume.load(track.id))
            startedAt = clock()
        }

    suspend fun stopAndRecord(track: Track) {
        val position = player.positionMs()
        val playTime = clock() - startedAt
        player.stop()
        resume.save(track.id, position)
        events.record(track, playedAtEpochMs = startedAt, playTimeMs = playTime)
    }
}
```

- [ ] **Step 5: Run to verify pass**

Run: `./gradlew :playback:testDebugUnitTest`
Expected: PASS, 3 tests.

- [ ] **Step 6: Add the Media3 service and adapters**

`playback/src/main/AndroidManifest.xml`:
```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
    <uses-permission android:name="android.permission.INTERNET" />
    <application>
        <service
            android:name="app.aria.playback.AriaPlaybackService"
            android:exported="true"
            android:foregroundServiceType="mediaPlayback">
            <intent-filter>
                <action android:name="androidx.media3.session.MediaSessionService" />
            </intent-filter>
        </service>
    </application>
</manifest>
```

`playback/src/main/kotlin/app/aria/playback/AriaPlaybackService.kt`:
```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.playback

import android.content.Intent
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/** Minimal foreground playback service. Real queue, crossfade and downloads arrive in sub-project 2. */
class AriaPlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val exo = ExoPlayer.Builder(this).build()
        session = MediaSession.Builder(this, exo).build()
        PlaybackHost.player = Media3Player(exo)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (session?.player?.playWhenReady != true) stopSelf()
    }

    override fun onDestroy() {
        PlaybackHost.player = null
        session?.run { player.release(); release() }
        session = null
        super.onDestroy()
    }
}

/**
 * Process-local handle the app uses to reach the running service's player.
 * TODO(sub-project 2): skeleton shortcut. Replace with a bound MediaController; do not build on this.
 */
object PlaybackHost {
    @Volatile var player: Player? = null
}

class Media3Player(private val exo: ExoPlayer) : Player {
    override fun play(url: String, startPositionMs: Long) {
        exo.setMediaItem(MediaItem.fromUri(url), startPositionMs)
        exo.prepare()
        exo.play()
    }
    override fun positionMs(): Long = exo.currentPosition
    override fun stop() = exo.stop()
}
```
`ResumeStore`'s Android implementation (SharedPreferences) lives in `:app` (Task 10) so `:playback` stays free of storage choices.

- [ ] **Step 7: Verify the boundary and commit**

Run: `./gradlew :playback:assembleDebug checkModuleGraph`
Expected: `BUILD SUCCESSFUL`.

```bash
git add -A && git commit -m "feat(playback): add minimal Media3 service and PlayerController behind domain interfaces"
```

---

### Task 10: `:app` walking skeleton, crash log, backup rules, flavors

**Files:**
- Create: `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/kotlin/app/aria/music/AriaApp.kt`, `app/src/main/kotlin/app/aria/music/MainActivity.kt`, `app/src/main/kotlin/app/aria/music/SkeletonScreen.kt`, `app/src/main/kotlin/app/aria/music/di/AppModule.kt`, `app/src/main/kotlin/app/aria/music/SharedPrefsResumeStore.kt`, `app/src/main/res/xml/data_extraction_rules.xml`, `app/src/main/res/xml/backup_rules.xml`, `core/src/main/kotlin/app/aria/core/crash/CrashReportFormatter.kt`, `core/src/test/kotlin/app/aria/core/crash/CrashReportFormatterTest.kt`, `scripts/check-backup-rules.sh`, `app/src/gms/AndroidManifest.xml`
- Modify: none

**Interfaces:**
- Consumes: everything above.
- Produces: `object CrashReportFormatter { fun format(t: Throwable, appVersion: String, sdkInt: Int, flavor: String): String }`.

**Acceptance Criteria:**
- `./gradlew :app:assembleFossDebug :app:assembleGmsDebug` succeed.
- The app label and `applicationId` come from `aria.properties` only: changing `app.name`/`app.id` there changes both with no other edit.
- The skeleton screen searches a query, shows results, plays the first track, and records a play event; recording is visible as the play count on the screen.
- Crash reports redact tokens, cookies and URL secrets, and the app has no Firebase or analytics dependency in either flavor (checked by grep in CI).
- Backup and data-extraction rules exclude the `secure/` area; `scripts/check-backup-rules.sh` passes.
- `gms` adds only a Cast placeholder manifest merge point now; Cast code is sub-project 6.

- [ ] **Step 1: Write the failing crash-format tests**

`core/src/test/kotlin/app/aria/core/crash/CrashReportFormatterTest.kt`:
```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.core.crash

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashReportFormatterTest {
    private fun report(message: String) =
        CrashReportFormatter.format(IllegalStateException(message), "0.1.0", 34, "foss")

    @Test fun includesVersionSdkFlavorAndExceptionType() {
        val r = report("boom")
        assertTrue(r.contains("Aria 0.1.0"))
        assertTrue(r.contains("SDK 34"))
        assertTrue(r.contains("flavor foss"))
        assertTrue(r.contains("IllegalStateException"))
    }

    @Test fun redactsQueryStringSecrets() {
        val r = report("GET https://x.invalid/a?sig=SECRET123&token=TOP&ok=1 failed")
        assertFalse(r.contains("SECRET123"))
        assertFalse(r.contains("TOP"))
        assertTrue(r.contains("ok=1"))
    }

    @Test fun redactsSpotifyCookieAndBearerTokens() {
        val r = report("sp_dc=AQCsecretcookie; Authorization: Bearer abc.def.ghi")
        assertFalse(r.contains("AQCsecretcookie"))
        assertFalse(r.contains("abc.def.ghi"))
    }
}
```

- [ ] **Step 2: Run to verify failure, then implement**

Run: `./gradlew :core:testDebugUnitTest --tests '*CrashReportFormatterTest'`
Expected: FAIL, unresolved `CrashReportFormatter`.

`core/src/main/kotlin/app/aria/core/crash/CrashReportFormatter.kt`:
```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.core.crash

/** Builds the text a user can copy into a GitHub issue. Secrets are redacted before anything is shown. */
object CrashReportFormatter {
    private val QUERY_SECRET = Regex("([?&](?:sig|signature|token|key|cookie|auth|authorization|access_token)=)[^&\\s]+", RegexOption.IGNORE_CASE)
    private val SP_DC = Regex("(sp_dc=)[^;\\s]+", RegexOption.IGNORE_CASE)
    private val BEARER = Regex("(Bearer\\s+)[A-Za-z0-9._~+/=-]+", RegexOption.IGNORE_CASE)

    fun format(t: Throwable, appVersion: String, sdkInt: Int, flavor: String): String {
        val trace = t.stackTraceToString()
        return buildString {
            appendLine("Aria $appVersion, SDK $sdkInt, flavor $flavor")
            appendLine(redact(trace))
        }
    }

    internal fun redact(text: String): String =
        text.replace(QUERY_SECRET, "$1[redacted]")
            .replace(SP_DC, "$1[redacted]")
            .replace(BEARER, "$1[redacted]")
}
```
Run: `./gradlew :core:testDebugUnitTest --tests '*CrashReportFormatterTest'`
Expected: PASS, 3 tests.

- [ ] **Step 3: Write `app/build.gradle.kts`**

```kotlin
import java.util.Properties

plugins {
    id("com.android.application")
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.ksp)
}

val brand = Properties().apply { file("../aria.properties").inputStream().use(::load) }

android {
    namespace = "app.aria.music"
    compileSdk = 37
    defaultConfig {
        applicationId = brand.getProperty("app.id")
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        resValue("string", "app_name", brand.getProperty("app.name"))
        buildConfigField("String", "BRAND_NAME", "\"${brand.getProperty("app.name")}\"")
    }
    buildFeatures { compose = true; buildConfig = true }
    flavorDimensions += listOf("variant")
    productFlavors {
        create("foss") { dimension = "variant" }
        create("gms") { dimension = "variant" }
    }
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a")
            isUniversalApk = true
        }
    }
    buildTypes {
        release { isMinifyEnabled = true }
        debug { applicationIdSuffix = ".debug" }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin { jvmToolchain(21) }

dependencies {
    implementation(project(":domain"))
    implementation(project(":core"))
    implementation(project(":data"))
    implementation(project(":designsystem"))
    implementation(project(":innertube"))
    implementation(project(":playback"))
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.activity.compose)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.material3)
    implementation(libs.room.runtime)
    implementation(libs.timber)
}
```
Do not add any `gmsImplementation` in this task.

- [ ] **Step 4: Backup rules and the checker**

`app/src/main/res/xml/data_extraction_rules.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup>
        <exclude domain="file" path="secure/" />
        <exclude domain="sharedpref" path="secure.xml" />
    </cloud-backup>
    <device-transfer>
        <exclude domain="file" path="secure/" />
        <exclude domain="sharedpref" path="secure.xml" />
    </device-transfer>
</data-extraction-rules>
```

`app/src/main/res/xml/backup_rules.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<full-backup-content>
    <exclude domain="file" path="secure/" />
    <exclude domain="sharedpref" path="secure.xml" />
</full-backup-content>
```

`scripts/check-backup-rules.sh`:
```bash
#!/usr/bin/env bash
set -euo pipefail
m=app/src/main/AndroidManifest.xml
grep -q 'android:dataExtractionRules="@xml/data_extraction_rules"' "$m" || { echo "manifest missing dataExtractionRules"; exit 1; }
grep -q 'android:fullBackupContent="@xml/backup_rules"' "$m" || { echo "manifest missing fullBackupContent"; exit 1; }
grep -q 'android:usesCleartextTraffic="false"' "$m" || { echo "cleartext traffic must be disabled"; exit 1; }
for f in app/src/main/res/xml/data_extraction_rules.xml app/src/main/res/xml/backup_rules.xml; do
  grep -q 'path="secure/"' "$f" || { echo "$f does not exclude secure/"; exit 1; }
done
if grep -rn -i -E "firebase|crashlytics|google-services" app/build.gradle.kts build.gradle.kts gradle/libs.versions.toml | grep -v -E "^\S+:\s*#"; then
  echo "Firebase or analytics reference found"; exit 1
fi
echo "ok"
```
Remove the Firebase-related lines Meld's catalog may carry (`grep -n -i firebase gradle/libs.versions.toml`) so this check passes.

- [ ] **Step 5: Manifest**

`app/src/main/AndroidManifest.xml`:
```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />
    <!-- Android 13+: without it the media notification is silently hidden. Requested at first play. -->
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <application
        android:name=".AriaApp"
        android:label="@string/app_name"
        android:allowBackup="true"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="@xml/backup_rules"
        android:usesCleartextTraffic="false">
        <activity android:name=".MainActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```
`app/src/gms/AndroidManifest.xml`:
```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android" />
```

- [ ] **Step 6: Application, DI, resume store**

`app/src/main/kotlin/app/aria/music/AriaApp.kt`:
```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music

import android.app.Application
import android.os.Build
import app.aria.core.crash.CrashReportFormatter
import dagger.hilt.android.HiltAndroidApp
import java.io.File

@HiltAndroidApp
class AriaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Release builds plant no verbose log tree; nothing logs tokens or cookies.
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val dir = File(filesDir, "crash").apply { mkdirs() }
                val flavor = if (BuildConfig.DEBUG) "debug" else "release"
                File(dir, "last.txt").writeText(
                    CrashReportFormatter.format(error, BuildConfig.VERSION_NAME, Build.VERSION.SDK_INT, flavor),
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }
}
```

`app/src/main/kotlin/app/aria/music/SharedPrefsResumeStore.kt`:
```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music

import android.content.Context
import app.aria.domain.TrackId
import app.aria.playback.ResumeStore

/** Resume positions are not secrets, so plain SharedPreferences is fine (the `secure/` area is for tokens). */
class SharedPrefsResumeStore(context: Context) : ResumeStore {
    private val prefs = context.getSharedPreferences("resume", Context.MODE_PRIVATE)
    override fun save(id: TrackId, positionMs: Long) { prefs.edit().putLong(id.raw, positionMs).apply() }
    override fun load(id: TrackId): Long = prefs.getLong(id.raw, 0L)
}
```

`app/src/main/kotlin/app/aria/music/di/AppModule.kt`:
```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.di

import android.content.Context
import androidx.room.Room
import app.aria.data.AriaDatabase
import app.aria.data.PlayEventRepositoryImpl
import app.aria.domain.PlayEventRepository
import app.aria.domain.SearchSource
import app.aria.domain.StreamSource
import app.aria.innertube.adapter.YouTubeSearchSource
import app.aria.innertube.adapter.YouTubeStreamSource
import app.aria.music.SharedPrefsResumeStore
import app.aria.playback.ResumeStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun database(@ApplicationContext c: Context): AriaDatabase =
        Room.databaseBuilder(c, AriaDatabase::class.java, "aria.db").build() // never fallbackToDestructiveMigration

    @Provides @Singleton fun playEvents(db: AriaDatabase): PlayEventRepository = PlayEventRepositoryImpl(db.playEventDao())
    @Provides @Singleton fun search(): SearchSource = YouTubeSearchSource()
    @Provides @Singleton fun streams(): StreamSource = YouTubeStreamSource()
    @Provides @Singleton fun resume(@ApplicationContext c: Context): ResumeStore = SharedPrefsResumeStore(c)
}
```

- [ ] **Step 7: Skeleton screen and activity**

`app/src/main/kotlin/app/aria/music/SkeletonScreen.kt`:
```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.aria.designsystem.GlassSurface
import app.aria.domain.PlayEventRepository
import app.aria.domain.SearchSource
import app.aria.domain.StreamSource
import app.aria.domain.Track
import app.aria.playback.AriaPlaybackService
import app.aria.playback.PlaybackHost
import app.aria.playback.PlayerController
import app.aria.playback.ResumeStore
import kotlinx.coroutines.launch

/** Walking skeleton: search -> resolve -> play -> record, crossing every module boundary. */
@Composable
fun SkeletonScreen(
    search: SearchSource,
    streams: StreamSource,
    events: PlayEventRepository,
    resume: ResumeStore,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Track>>(emptyList()) }
    var status by remember { mutableStateOf("") }
    var playing by remember { mutableStateOf<Track?>(null) }
    var plays by remember { mutableStateOf(0) }
    // One controller per playback session: it remembers the start time that Stop needs.
    var controller by remember { mutableStateOf<PlayerController?>(null) }
    // Android 13+: ask once at first play. Playback continues if the user says no (no notification then).
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    GlassSurface(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(BuildConfig.BRAND_NAME)
            OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Search") })
            Button(onClick = {
                scope.launch {
                    search.searchTracks(query)
                        .onSuccess { results = it; status = "${it.size} results" }
                        .onFailure { status = "Search failed: ${it.javaClass.simpleName}" }
                }
            }) { Text("Search") }
            Text(status)
            playing?.let { t ->
                Text("Playing: ${t.title} (plays recorded: $plays)")
                Button(onClick = {
                    scope.launch {
                        controller?.stopAndRecord(t)
                        plays = events.playCount(t.id)
                        playing = null
                        controller = null
                    }
                }) { Text("Stop") }
            }
            LazyColumn {
                items(results) { t ->
                    Button(onClick = {
                        scope.launch {
                            if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            // startService, not startForegroundService: Media3 promotes the service itself once playback
                            // starts, whereas startForegroundService crashes (ForegroundServiceDidNotStartInTimeException)
                            // if playback never starts, for example when stream resolution fails.
                            context.startService(Intent(context, AriaPlaybackService::class.java))
                            kotlinx.coroutines.delay(500) // skeleton shortcut: service creates PlaybackHost.player on start
                            val player = PlaybackHost.player ?: run { status = "Player not ready"; return@launch }
                            val c = PlayerController(streams, events, player, resume, System::currentTimeMillis)
                            c.start(t)
                                .onSuccess { controller = c; playing = t; plays = events.playCount(t.id) }
                                .onFailure { status = "Cannot play: ${it.message}" }
                        }
                    }) { Text("${t.title} - ${t.artists.joinToString()}") }
                }
            }
        }
    }
}
```
The 500 ms delay and the `PlaybackHost` singleton are skeleton shortcuts and must not be copied into production code; sub-project 2 replaces both with a bound `MediaController` (the `TODO(sub-project 2)` marker on `PlaybackHost` is the hand-off). Record that in the commit message. The service start uses `startService` on purpose; a reviewer who "fixes" it to `startForegroundService` reintroduces the crash described in the code comment.

`app/src/main/kotlin/app/aria/music/MainActivity.kt`:
```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import app.aria.domain.PlayEventRepository
import app.aria.domain.SearchSource
import app.aria.domain.StreamSource
import app.aria.playback.ResumeStore
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var search: SearchSource
    @Inject lateinit var streams: StreamSource
    @Inject lateinit var events: PlayEventRepository
    @Inject lateinit var resume: ResumeStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { SkeletonScreen(search, streams, events, resume) } }
    }
}
```

- [ ] **Step 8: Verify the build, checks and rename property**

Run:
```bash
./gradlew :app:assembleFossDebug :app:assembleGmsDebug checkModuleGraph
chmod +x scripts/check-backup-rules.sh && scripts/check-backup-rules.sh
sed -i 's/^app.name=Aria$/app.name=RenameProbe/' aria.properties && ./gradlew :app:processFossDebugResources && grep -rn "RenameProbe" app/build/generated/res | head -2
sed -i 's/^app.name=RenameProbe$/app.name=Aria/' aria.properties
```
Expected: both builds `BUILD SUCCESSFUL`; checker prints `ok`; the grep finds `RenameProbe` in generated resources (proving the single source), and the final sed restores `Aria`.

- [ ] **Step 9: Manual check on a device or emulator (API 34)**

Install `fossDebug`, search "daft punk", tap a result, confirm audio plays and notification shows, tap Stop, confirm "plays recorded: 1". If the result list is empty or playback fails, record the exact error in the commit message; a `StreamUnavailableException` here is the known limit of the skeleton resolver (signature or PoToken needed), and the follow-up goes to sub-project 2, not into this task.

- [ ] **Step 10: Commit**

```bash
git add -A && git commit -m "feat(app): walking skeleton, crash log, backup rules, foss/gms flavors"
```

---

### Task 11: CI, nightly smoke test, provenance and license gates

**Files:**
- Modify: `.github/workflows/ci.yml` (created in Task 1; this task adds jobs and steps to it)
- Create: `.github/workflows/smoke.yml`, `.github/dependabot.yml`, `scripts/check-actions-pinned.sh`, `gradle/verification-metadata.xml`, `scripts/check-provenance.py`, `scripts/test_check_provenance.py`, `innertube/src/test/kotlin/app/aria/innertube/smoke/PlaybackSmokeTest.kt`
- Modify: `innertube/build.gradle.kts` (smoke test exclusion), `build.gradle.kts` (license report plugin)

**Interfaces:**
- Consumes: `docs/provenance.csv`, module list from `settings.gradle.kts`.
- Produces: `check-provenance.py` exits non-zero if any module directory lacks a valid ledger row.

**Acceptance Criteria:**
- `ci.yml` runs on every PR and push: builds `foss` and `gms` debug, unit tests for every module, `checkModuleGraph`, the backup-rules and destructive-migration scripts, the provenance check, and fails if `data/schemas` changed without a matching tracked file.
- `check-provenance.py` fails for: a module directory with no row; a row whose `kind` is not one of `copied|adapted|inspired|original`; a non-`original` row without a 40-hex `origin_commit` or without `origin_license`.
- `smoke.yml` runs nightly, resolves three fixed public tracks through `YouTubeStreamSource`, and opens one GitHub issue on failure (later failures comment on it), and fails if the smoke test was skipped rather than run.
- Every `uses:` in `.github/workflows` is pinned to a full commit SHA (enforced by `check-actions-pinned.sh`), the Gradle wrapper is validated, Dependabot covers Actions and Gradle, and `gradle/verification-metadata.xml` is committed and enforced.
- The license report job fails on a dependency whose license is not on the allowlist (Apache-2.0, MIT, BSD, GPL-3.0, LGPL, MPL-2.0, EPL, CC0, public domain, Unicode/ICU).

- [ ] **Step 1: Write the failing provenance tests**

`scripts/test_check_provenance.py`:
```python
# SPDX-License-Identifier: GPL-3.0-only
import subprocess, sys, textwrap, pathlib, tempfile

SCRIPT = pathlib.Path(__file__).with_name("check-provenance.py")
SHA = "a" * 40

def run(settings: str, csv: str, dirs: list[str]):
    with tempfile.TemporaryDirectory() as d:
        root = pathlib.Path(d)
        (root / "settings.gradle.kts").write_text(settings)
        (root / "docs").mkdir()
        (root / "docs" / "provenance.csv").write_text(csv)
        for name in dirs:
            (root / name).mkdir()
        p = subprocess.run([sys.executable, str(SCRIPT), str(root)], capture_output=True, text=True)
        return p.returncode, p.stdout + p.stderr

HEADER = "path,origin_repo,origin_commit,origin_license,kind,notes\n"
SETTINGS = 'include(":domain", ":data")'

def test_passes_when_every_module_has_a_row():
    code, _ = run(SETTINGS, HEADER + f"domain,,,,original,x\ndata,o/r,{SHA},GPL-3.0,adapted,x\n", ["domain", "data"])
    assert code == 0

def test_fails_when_a_module_has_no_row():
    code, out = run(SETTINGS, HEADER + "domain,,,,original,x\n", ["domain", "data"])
    assert code != 0 and "data" in out

def test_fails_on_invalid_kind():
    code, out = run(SETTINGS, HEADER + "domain,,,,original,x\ndata,o/r," + SHA + ",GPL-3.0,borrowed,x\n", ["domain", "data"])
    assert code != 0 and "kind" in out

def test_fails_when_ported_row_lacks_commit_or_license():
    code, _ = run(SETTINGS, HEADER + "domain,,,,original,x\ndata,o/r,,GPL-3.0,adapted,x\n", ["domain", "data"])
    assert code != 0
    code, _ = run(SETTINGS, HEADER + f"domain,,,,original,x\ndata,o/r,{SHA},,adapted,x\n", ["domain", "data"])
    assert code != 0

def test_subpath_rows_cover_their_module():
    code, _ = run(SETTINGS, HEADER + f"domain,,,,original,x\ndata/src/entities,o/r,{SHA},GPL-3.0,adapted,x\n", ["domain", "data"])
    assert code == 0
```

- [ ] **Step 2: Run to verify failure**

Run: `python3 -m pytest scripts/test_check_provenance.py -q`
Expected: FAIL, because `check-provenance.py` does not exist.

- [ ] **Step 3: Implement the check**

`scripts/check-provenance.py`:
```python
#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Fails if a Gradle module has no provenance ledger row, or if a row is malformed."""
import csv, pathlib, re, sys

KINDS = {"copied", "adapted", "inspired", "original"}
SHA = re.compile(r"^[0-9a-f]{40}$")

def main(root: pathlib.Path) -> int:
    settings = (root / "settings.gradle.kts").read_text()
    modules = sorted({m.lstrip(":") for m in re.findall(r'"(:[A-Za-z0-9_-]+)"', settings)})
    with open(root / "docs" / "provenance.csv", newline="") as fh:
        rows = list(csv.DictReader(fh))
    errors: list[str] = []
    for r in rows:
        if r["kind"] not in KINDS:
            errors.append(f"{r['path']}: invalid kind '{r['kind']}' (use {sorted(KINDS)})")
        if r["kind"] != "original":
            if not SHA.match(r["origin_commit"] or ""):
                errors.append(f"{r['path']}: origin_commit must be a 40-hex SHA")
            if not (r["origin_license"] or "").strip():
                errors.append(f"{r['path']}: origin_license is required for non-original code")
    for m in modules:
        if not any(r["path"] == m or r["path"].startswith(m + "/") for r in rows):
            errors.append(f"module '{m}' has no row in docs/provenance.csv")
    for e in errors:
        print("provenance:", e, file=sys.stderr)
    return 1 if errors else 0

if __name__ == "__main__":
    sys.exit(main(pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else ".")))
```

Run: `python3 -m pytest scripts/test_check_provenance.py -q && python3 scripts/check-provenance.py .`
Expected: 5 passed; the real check fails until every module row exists. Add rows for `core`, `designsystem`, `playback`, `app` (kind `original`) to `docs/provenance.csv`, then re-run: exit 0.

- [ ] **Step 4: Smoke test (excluded from normal runs)**

`innertube/src/test/kotlin/app/aria/innertube/smoke/PlaybackSmokeTest.kt`:
```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.innertube.smoke

import app.aria.domain.TrackId
import app.aria.innertube.adapter.YouTubeStreamSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Nightly early-warning for YouTube breakage. Runs only with -PrunSmoke (see innertube/build.gradle.kts). */
class PlaybackSmokeTest {
    private val ids = listOf("dQw4w9WgXcQ", "kJQP7kiw5Fk", "9bZkp7q19f0")

    @Test fun fixedPublicTracksResolveToAnAudioUrl() = runBlocking {
        assumeTrue(System.getProperty("aria.smoke") == "true")
        val source = YouTubeStreamSource()
        val failures = ids.filter { id -> source.resolve(TrackId.youtube(id)).isFailure }
        assertTrue("Could not resolve: $failures", failures.isEmpty())
    }
}
```
Add to `innertube/build.gradle.kts`:
```kotlin
tasks.withType<Test>().configureEach {
    systemProperty("aria.smoke", providers.gradleProperty("runSmoke").orNull ?: "false")
}
```
Run: `./gradlew :innertube:testDebugUnitTest`
Expected: PASS with the smoke test skipped (assumption not met).

- [ ] **Step 5: Write the workflows**

Replace the minimal `ci.yml` from Task 1 with this fuller one (same triggers; pin per Step 5b):
```yaml
name: ci
on:
  push: { branches: [main] }
  pull_request:
permissions: { contents: read }
concurrency:
  group: ci-${{ github.ref }}
  cancel-in-progress: ${{ github.event_name == 'pull_request' }}
jobs:
  verify:
    runs-on: ubuntu-latest
    timeout-minutes: 45
    steps:
      - uses: actions/checkout@v4
        with: { persist-credentials: false }
      - uses: gradle/actions/wrapper-validation@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: 21 }
      - uses: gradle/actions/setup-gradle@v4
      - name: Static checks
        run: |
          python3 -m pip install pytest
          python3 -m pytest scripts -q
          python3 scripts/check-provenance.py .
          scripts/check-backup-rules.sh
          scripts/check-no-destructive-migration.sh
          scripts/check-actions-pinned.sh
          test -f gradle/verification-metadata.xml
      - name: Build-logic tests (module-graph rules)
        run: ./gradlew -p buildSrc test
      - name: Module graph
        run: ./gradlew checkModuleGraph
      - name: Unit tests (all modules)
        run: ./gradlew test
      - name: Build both flavors
        run: ./gradlew :app:assembleFossDebug :app:assembleGmsDebug
      - name: Schema is committed and unchanged
        # git diff ignores untracked files, so a forgotten new version file (2.json) would pass; status sees both.
        run: |
          out="$(git status --porcelain -- data/schemas)"
          if [ -n "$out" ]; then echo "Room schema changed or uncommitted:"; echo "$out"; exit 1; fi
      - name: Dependency licenses
        run: ./gradlew checkLicense
  glass-ui:
    runs-on: ubuntu-latest
    timeout-minutes: 30
    strategy:
      matrix: { api: [26, 34] }
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: 21 }
      - uses: gradle/actions/setup-gradle@v4
      - name: Enable KVM
        run: |
          echo 'KERNEL=="kvm", GROUP="kvm", MODE="0666", OPTIONS+="static_node=kvm"' | sudo tee /etc/udev/rules.d/99-kvm4all.rules
          sudo udevadm control --reload-rules && sudo udevadm trigger --name-match=kvm
      - uses: reactivecircus/android-emulator-runner@v2
        with:
          api-level: ${{ matrix.api }}
          arch: x86_64
          script: ./gradlew :designsystem:connectedDebugAndroidTest
```

`.github/workflows/smoke.yml`:
```yaml
name: nightly-playback-smoke
on:
  schedule: [{ cron: "17 3 * * *" }]
  workflow_dispatch:
permissions: { contents: read, issues: write }
jobs:
  smoke:
    runs-on: ubuntu-latest
    timeout-minutes: 20
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: 21 }
      - uses: gradle/actions/setup-gradle@v4
      - run: ./gradlew :innertube:testDebugUnitTest -PrunSmoke=true --tests '*PlaybackSmokeTest'
      - name: Fail if the smoke test was skipped instead of run
        # assumeTrue skips silently when the property does not reach the test JVM; a skipped nightly must not look green.
        run: |
          f=$(ls innertube/build/test-results/testDebugUnitTest/*PlaybackSmokeTest.xml)
          grep -q 'tests="1"' "$f" && grep -q 'skipped="0"' "$f" || { echo "Smoke test did not run:"; head -3 "$f"; exit 1; }
      - name: Report failure (one open issue, not one per night)
        if: failure()
        env: { GH_TOKEN: "${{ github.token }}" }
        run: |
          run_url="$GITHUB_SERVER_URL/$GITHUB_REPOSITORY/actions/runs/$GITHUB_RUN_ID"
          gh label create smoke-failure --color B60205 --description "Nightly playback smoke test" 2>/dev/null || true
          n=$(gh issue list --label smoke-failure --state open --json number --jq '.[0].number // empty')
          if [ -n "$n" ]; then
            gh issue comment "$n" --body "Failed again ($(date -u +%F)): $run_url"
          else
            gh issue create --label smoke-failure --title "Nightly playback smoke test failed ($(date -u +%F))" \
              --body "See run: $run_url. Note: YouTube may block datacenter IPs; if this fails every night with no code change, run the same command from the maintainer's machine on a schedule instead."
          fi
```

- [ ] **Step 5b: Supply-chain hardening (stress-test branch 8)**

The YAML above shows tags for readability; after this step every `uses:` line is pinned.

1. Pin check. `scripts/check-actions-pinned.sh` (already run in the Static checks step):
```bash
#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-only
# Fails if any workflow step uses an Action that is not pinned to a full 40-hex commit SHA.
set -euo pipefail
root="${1:-.}"
bad=$(grep -rnE '^\s*-?\s*uses:\s' "$root/.github/workflows" 2>/dev/null \
  | grep -vE 'uses:\s*\./' \
  | grep -vE 'uses:\s*[^@[:space:]]+@[0-9a-f]{40}(\s|$)' || true)
if [ -n "$bad" ]; then
  echo "Actions must be pinned to a full commit SHA (add the version as a trailing comment):" >&2
  echo "$bad" >&2
  exit 1
fi
echo "all workflow actions are SHA-pinned"
```
A dry run on a scratch workflow gave exit 1 for `actions/checkout@v4` and exit 0 once it was replaced by a 40-hex SHA with a trailing `# v4` comment. Make it executable with `chmod +x`.

2. Pin every `uses:` in `ci.yml` and `smoke.yml`. For each action and tag, resolve the SHA (needs network and a working `gh`), then rewrite the line as `uses: owner/repo@<sha> # vX.Y.Z`:
```bash
gh api repos/actions/checkout/git/ref/tags/v4 --jq '.object | "\(.type) \(.sha)"'
# if type is "tag" (annotated), dereference once more:
gh api repos/actions/checkout/git/tags/<sha> --jq '.object.sha'
```
Use the full tag (for example `v4.2.2`), not a moving major tag, so the comment says exactly what was pinned. Run `scripts/check-actions-pinned.sh .` until it exits 0. Actions to pin: `actions/checkout`, `actions/setup-java`, `gradle/actions/setup-gradle`, `gradle/actions/wrapper-validation`, `reactivecircus/android-emulator-runner`.

3. `.github/dependabot.yml`:
```yaml
version: 2
updates:
  - package-ecosystem: github-actions
    directory: /
    schedule: { interval: weekly }
    groups: { actions: { patterns: ["*"] } }
  - package-ecosystem: gradle
    directory: /
    schedule: { interval: weekly }
    groups: { gradle-deps: { patterns: ["*"] } }
```

4. Gradle dependency verification. With network access, generate and commit the checksums:
```bash
./gradlew --write-verification-metadata sha256 :app:assembleFossDebug :app:assembleGmsDebug test
git add gradle/verification-metadata.xml
```
When the file exists Gradle enforces it on every build, so CI fails on any dependency whose checksum is missing or different. After any dependency bump (including Dependabot PRs, which cannot regenerate the file) run `./gradlew --write-verification-metadata sha256 help`, review the diff, and commit it. Record this in `CONTRIBUTING.md` (Task 12). A reviewer rejects any wildcard `<trusted-artifacts>` entry.

Run: `scripts/check-actions-pinned.sh . && ./gradlew :app:assembleFossDebug`
Expected: `all workflow actions are SHA-pinned`, then BUILD SUCCESSFUL under verification. If the sandbox has no network, do the pinning and checksum generation on the first run that has it (the Prerequisite already requires the GitHub repo).

- [ ] **Step 6: License gate**

In root `build.gradle.kts` add to the `plugins` block: `id("com.github.jk1.dependency-license-report") version "2.9"`, then:
```kotlin
import com.github.jk1.license.filter.LicenseBundleNormalizer
import com.github.jk1.license.render.JsonReportRenderer

licenseReport {
    allowedLicensesFile = file("config/allowed-licenses.json")
    renderers = arrayOf(JsonReportRenderer())
    filters = arrayOf(LicenseBundleNormalizer())
}
```
The plugin registers `checkLicense` itself when `allowedLicensesFile` is set; do not register a task of that name. Create `config/allowed-licenses.json`:
```json
{
  "allowedLicenses": [
    { "moduleLicense": "The Apache Software License, Version 2.0" },
    { "moduleLicense": "Apache-2.0" },
    { "moduleLicense": "MIT License" },
    { "moduleLicense": "MIT" },
    { "moduleLicense": "BSD-3-Clause" },
    { "moduleLicense": "BSD-2-Clause" },
    { "moduleLicense": "GNU General Public License, version 3" },
    { "moduleLicense": "GPL-3.0" },
    { "moduleLicense": "LGPL-2.1" },
    { "moduleLicense": "MPL-2.0" },
    { "moduleLicense": "EPL-1.0" },
    { "moduleLicense": "CC0-1.0" },
    { "moduleLicense": "Unicode/ICU License" }
  ]
}
```
The plugin's task wiring for `allowedLicensesFile` is `checkLicense`; if the plugin version above is incompatible with Gradle 9.7 or AGP 9.3, pick the newest release that supports Gradle 9 and record the version in the commit message. Run: `./gradlew checkLicense`. Expected: passes, or lists dependencies whose license needs a decision from the maintainer (do not widen the allowlist to silence it).

- [ ] **Step 7: Commit**

```bash
git add -A && git commit -m "ci: add build/test/provenance/license gates and nightly playback smoke test"
```

---

### Task 12: Documentation, provider records, data-flow and release gates

**Files:**
- Create: `docs/providers/youtube-music.md`, `docs/providers/innertubex.md`, `docs/data-flow.md`, `docs/release-gates.md`, `PRIVACY.md`, `CONTRIBUTING.md`
- Modify: `README.md`, `CREDITS.md`, `gradle/libs.versions.toml` (trim unused entries)

**Interfaces:**
- Consumes: Task 0 findings.

**Acceptance Criteria:**
- Every provider the foundation talks to has a record with endpoint, tier, owner, kill switch, fallback, data sent, when sent, and opt-in status. A provider with no record is not added to the app.
- `docs/data-flow.md` lists exactly the data the foundation sends, derived from the provider records, and `PRIVACY.md` states plainly: no telemetry, and which third parties receive what.
- `docs/release-gates.md` lists, as unchecked boxes, the gates the spec says must pass before specific builds.
- `CREDITS.md` has a specific "what was taken / copied or adapted" line for every ledger row.

- [ ] **Step 1: Write the YouTube provider record**

`docs/providers/youtube-music.md`:
```markdown
# Provider: YouTube Music

- Tier: 2 (unofficial use of a commercial service's endpoints)
- Endpoint: `music.youtube.com` / `youtubei` endpoints via InnerTubeX
- Owner: Google (no relationship with Aria)
- Default: on (the app cannot work without it)
- Kill switch: registry key `killSwitches.youtube` (data only)
- Fallback: none; the app shows "YouTube Music is unavailable" and keeps downloaded and local tracks playable
- Data sent: search queries, video IDs, device/client identifiers the client library sends
- When: on search and on playback only; no background calls from the foundation
- Opt-in: not applicable (core feature); disclosed in the first-run text and PRIVACY.md
```

- [ ] **Step 2: Write the InnerTubeX record from the Task 0 findings**

`docs/providers/innertubex.md`: copy the license name, store-policy finding and PoToken finding from `docs/spikes/2026-10-innertubex.md`, with the same fields as above (it is a library, so "Data sent: none of its own; it is the transport for the YouTube Music record").

- [ ] **Step 3: Write data-flow and privacy text**

`docs/data-flow.md`: a table with one row per provider record (columns: provider, data, when, opt-in), currently one row for YouTube Music.

`PRIVACY.md`:
```markdown
# Privacy

Aria has no telemetry, analytics, advertising or crash-reporting service. Nothing is sent to the Aria project.

When you search or play music, the app contacts YouTube Music to do it. That request contains your search text or the video ID, and the identifiers the YouTube client library normally sends. See `docs/data-flow.md` for the full list, which is generated from the provider records.

If the app crashes it writes a report to your device. It is shown only on your device, and you decide whether to copy it into a GitHub issue. Tokens, cookies and URL secrets are removed before it is shown.

Aria is not affiliated with Google or YouTube.
```
Add a sentence per provider here whenever a provider record is added.

- [ ] **Step 4: Release gates**

`docs/release-gates.md`:
```markdown
# Release gates

## Before the first public build (alphas included)
- [ ] Final app name chosen; trademark and store-name check done (GitHub, F-Droid, Play, trademark search). "Aria" is crowded.
- [ ] Final package ID chosen, tied to a domain or account the maintainer controls; `aria.properties` updated.
- [ ] Repo on an account with 2FA; Codeberg mirror configured; APK signing key backed up offline.
- [ ] Non-affiliation notice present in README and the About screen.

## Before the first beta
- [ ] License and provenance audit passes: every ported directory has a ledger row, upstream license read, SPDX headers present, font license texts included (or fonts replaced).
- [ ] Vendored native code (`vibrafp`) audited or excluded.
- [ ] Kyant0/backdrop upstream `NOTICE` file checked (copy to `licenses/NOTICE` if it exists); `licenses/Apache-2.0.txt` present.
- [ ] Privacy policy matches `docs/data-flow.md` for every provider shipped.
- [ ] Room schema frozen; every change after this needs a tested migration.

## Before v1.0
- [ ] Google Play policy position confirmed (assumed unavailable until checked).
- [ ] IzzyOnDroid and F-Droid metadata and review requirements met.
- [ ] Spotify registry signing key stored only in CI secrets, separate from the APK key; two public keys embedded.
- [ ] Discord presence: auth flow read and classified, or feature left out (spec 7.2).
```

- [ ] **Step 5: CONTRIBUTING, CREDITS lines, catalog trim**

`CONTRIBUTING.md`: states the rules a contributor must follow (GPL-3.0-only header on every new file; new provider needs a record in `docs/providers/` first; ported code needs a ledger row and an upstream license check before the PR; module dependency rules enforced by `checkModuleGraph`; no telemetry; Actions stay SHA-pinned; after any dependency bump regenerate `gradle/verification-metadata.xml` with `./gradlew --write-verification-metadata sha256 help` and review the diff).

Update `CREDITS.md` so each ledger row has a specific line (for example: "`innertube`: Meld's module, copied with package rename, wraps InnerTubeX; parsing code remains from Meld/Metrolist").

Trim the catalog: for each alias in `gradle/libs.versions.toml`, `grep -rn "libs\.<alias with dots>" --include=*.kts .`; delete aliases with no match and every Firebase, Cast and Qobuz-related entry not used by a current module. Run `./gradlew help` after to confirm the catalog still parses.

- [ ] **Step 6: Final verification and commit**

Run:
```bash
./gradlew test checkModuleGraph :app:assembleFossDebug :app:assembleGmsDebug
python3 scripts/check-provenance.py . && scripts/check-backup-rules.sh && scripts/check-no-destructive-migration.sh
```
Expected: all succeed.

```bash
git add -A && git commit -m "docs: provider records, data-flow, privacy, release gates, contributing"
```

---

## Spec Coverage

| Spec requirement | Task |
|---|---|
| Module layout (section 3), dependency rule | 1, 2, 3 (rule enforced), 8, 9 |
| Room v1, no legacy migrations, schema exported, migration harness, no destructive fallback | 4 (+ guard script), 5 |
| `TrackId` and Spotify-tracks-are-not-songs | 2; Spotify side in sub-project 5 (see Deferred) |
| Generic `track_match` table | 4 |
| Shared core entities from Echo/Meld (not Spotify/Qobuz tables) | 5 |
| Secrets never in Room | 4, 5 (no token entity ported); token storage in sub-project 5 |
| `GlassSurface`, glass fallback, Auto/On/Off, battery saver, tokens | 7 |
| Two player skins, accessibility rules | Deferred to sub-project 3 |
| `StreamSource` seam for future lossless | 2, 8 |
| Walking skeleton end to end | 8, 9, 10 |
| Provider policy, vetting records, data-sent field | 12 (records), 7.1 rules enforced by contributing rule |
| Signed registry: schema validation, Ed25519, two keys, max age, last-known-good | 6 |
| Registry-delivered YouTube client config (data only) | 6 (`youtubeClients`), consumed in sub-project 2 |
| Encrypted tokens excluded from backup | Backup rules in 10; encrypted storage in sub-project 5 |
| Login WebView limits, TOTP via signed registry | Deferred to sub-project 5 (registry carries `totpSecret`, Task 6) |
| No Firebase/analytics; crash log with redaction | 10, 11 (grep check in `check-backup-rules.sh`) |
| Cleartext disabled | 10 |
| Flavors `foss`/`gms`, ABI splits | 10 |
| CI: build both flavors, lint, tests, schema check, license scan | 11 (lint: add `./gradlew lint` to `ci.yml` once the first lint baseline is generated) |
| Nightly playback smoke test | 11 |
| Own registry host, no dependence on Meld's | 6 (client), hosting in sub-project 5 |
| Provenance ledger + CI check, SPDX headers, audit before port | 1, 5, 7, 8, 11 |
| `CREDITS.md`, non-affiliation notice | 1, 12 |
| Name/package ID from one location | 1, 10 |
| Release gates (name, package ID, license audit, Play, store) | 12 |
| InnerTubeX license/store/PoToken checks | 0 |
| Privacy policy and data-flow table | 12 |
| Fonts license texts; `vibrafp` audit | 12 (gates); executed when those assets are ported |

### Deliberate deferrals (surfaced, not silent)

These spec items are **not** built in this plan, by design, because they belong to later sub-projects. Each needs a task in that sub-project's plan.

1. Spotify token encryption, login WebView lockdown, TOTP flow, hash registry hosting (sub-project 5).
2. Material and Apple-style player skins; accessibility pass on the real UI (sub-project 3).
3. Backup and restore feature, Chromecast, Listen Together relay, Discord decision, USB audio/DSP, recognition, Canvas, widget, Android Auto, Last.fm (sub-project 6).
4. Shrinking Meld's inherited response-parsing code (tracked follow-up, not a v1 promise).
5. `./gradlew lint` baseline and CI step (add after the first baseline exists; flagged in the table above rather than silently dropped).
6. Fonts: none are ported in this plan, so their license texts are a gate for the sub-project that adds them.

## Stress Test Results

Run 2026-10-30, Mode A (edits applied inline). Eight branches, each answered "Agree"; all eight changed the plan.

| # | Branch | Finding | Change |
|---|--------|---------|--------|
| 1 | Verification and delivery | Nothing can be compiled or pushed from the planning sandbox | Prerequisite (GitHub repo, push access) and a Definition of done of green CI per task |
| 2 | Module and build structure | `settings.gradle.kts` listing absent modules fails under Gradle 9; `build-logic` convention plugins cannot be verified under AGP 9 | Include each module in its own task; explicit per-module files plus `buildSrc` |
| 3 | Registry verifier | Future-dated payloads, rollback and cross-protocol signature reuse were possible | Future-skew rejection, anti-rollback in `RegistryRepository.accept`, `aria-registry-v1` signing context |
| 4 | Entity port | Glob missed nested backdrop dirs; `@Database` lacked views; port scan needed a limit | `find`-based rename, views and `SchemaContentTest`, `scripts/port-scan.sh` with a limit of 10 |
| 5 | Glass provenance | Backdrop code is Apache-2.0 (Kyant0), not GPL-3.0; the old step would have stopped or mislabelled it | Vendor with Apache-2.0 attribution: license text, per-file modified notice, `CREDITS.md`, two ledger rows, NOTICE check as a beta gate |
| 6 | Service start | `startForegroundService` crashes if playback never starts; no notification permission on Android 13+ | `startService`, first-play `POST_NOTIFICATIONS` request, `PlaybackHost` marked `TODO(sub-project 2)` |
| 7 | CI correctness | Schema check ignored untracked files; smoke test could skip silently; duplicate nightly issues; `buildSrc` tests not run | `git status --porcelain` check, skipped-test failure, one labelled issue, `-p buildSrc test`, timeouts and concurrency |
| 8 | Supply chain | Actions on mutable tags; wrapper unvalidated; no dependency pinning (InnerTubeX comes from JitPack) | Step 5b: SHA-pinned Actions with enforcing script, wrapper validation, Dependabot, committed Gradle dependency verification |

Remaining risks, all needing network or the maintainer: Task 0 spike results (InnerTubeX license and store policy, PoToken), upstream Kyant0 NOTICE file, "Aria" name and trademark clearance, and the SHA and checksum generation in Task 11 Step 5b.
