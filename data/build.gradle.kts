// SPDX-License-Identifier: GPL-3.0-only
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
}

// Room schema JSON is read by the migration test as an asset (AGP 9 variant API; the old sourceSets DSL is gone).
androidComponents {
    onVariants { variant ->
        (variant as? com.android.build.api.variant.HasUnitTest)
            ?.unitTest?.sources?.assets?.addStaticSourceDirectory("$projectDir/schemas")
    }
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
