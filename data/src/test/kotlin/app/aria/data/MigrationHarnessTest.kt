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
