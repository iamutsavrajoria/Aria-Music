// SPDX-License-Identifier: GPL-3.0-only
plugins {
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.kotlin.ksp) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

// Puts AGP and the Kotlin Gradle plugin on the classpath so modules can use
// id("com.android.library") and id("org.jetbrains.kotlin.jvm") without a version,
// exactly as Meld's root build does.
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath(libs.gradle)
        classpath(kotlin("gradle-plugin", libs.versions.kotlin.get()))
    }
}
