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

