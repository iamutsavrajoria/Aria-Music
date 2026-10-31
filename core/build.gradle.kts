// SPDX-License-Identifier: GPL-3.0-only
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
    // kotlinx-serialization-json has no catalog alias; the ktor serialization artifact exposes it as an api dependency.
    implementation(libs.ktor.serialization.json)
    testImplementation(libs.junit)
}
