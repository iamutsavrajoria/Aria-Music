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
