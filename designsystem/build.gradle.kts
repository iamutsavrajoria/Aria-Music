// SPDX-License-Identifier: GPL-3.0-only
plugins {
    id("com.android.library")
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "app.aria.designsystem"
    compileSdk = 37
    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
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
    // The vendored backdrop code uses androidx.compose.ui.util (fastCoerceAtMost, lerp).
    implementation(libs.compose.ui.util)
    implementation(libs.compose.runtime)
    implementation(libs.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.compose.ui.test.junit4)
    // Supplies the empty host activity that createComposeRule launches.
    debugImplementation(libs.compose.ui.test.manifest)
}
