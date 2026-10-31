// SPDX-License-Identifier: GPL-3.0-only
import com.github.jk1.license.filter.LicenseBundleNormalizer
import com.github.jk1.license.render.JsonReportRenderer

plugins {
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.kotlin.ksp) apply false
    alias(libs.plugins.androidx.room) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    // 3.1.4 is the newest release; 2.9 (the plan's pin) predates Gradle 9 support. Not configuration-cache compatible
    // (CI runs checkLicense without it) and needs --no-parallel on Gradle 9 multi-project builds.
    id("com.github.jk1.dependency-license-report") version "3.1.4"
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

tasks.register("checkModuleGraph") {
    group = "verification"
    description = "Fails if a module depends on one it is not allowed to."
    doLast {
        val graph = subprojects.associate { p ->
            p.path to p.configurations
                .flatMap { it.dependencies.withType(org.gradle.api.artifacts.ProjectDependency::class.java) }
                .map { it.path }
                .filter { it != p.path } // AGP adds self-dependencies to some configurations; they are harmless
                .toSet()
        }
        val violations = ModuleGraphRules.violations(graph)
        if (violations.isNotEmpty()) throw GradleException("Module graph violations:\n" + violations.joinToString("\n"))
    }
}

// The plugin registers `checkLicense` itself because allowedLicensesFile is set; do not register a task of that name.
// Android modules have no `runtimeClasspath`, so scan the release runtime classpaths (names absent from a project are skipped).
licenseReport {
    configurations = arrayOf("releaseRuntimeClasspath", "fossReleaseRuntimeClasspath", "gmsReleaseRuntimeClasspath")
    allowedLicensesFile = file("config/allowed-licenses.json")
    renderers = arrayOf(JsonReportRenderer())
    filters = arrayOf(LicenseBundleNormalizer())
}
