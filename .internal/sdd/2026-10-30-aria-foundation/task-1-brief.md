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

