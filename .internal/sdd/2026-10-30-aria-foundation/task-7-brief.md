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

