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

