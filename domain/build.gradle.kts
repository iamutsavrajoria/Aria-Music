// SPDX-License-Identifier: GPL-3.0-only
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("java-test-fixtures")
}

kotlin { jvmToolchain(21) }

tasks.withType<Test> { useJUnit() }

dependencies {
    api(libs.coroutines.core)
    testFixturesApi(libs.coroutines.core)
    testFixturesImplementation(kotlin("test"))
    testImplementation(kotlin("test"))
}
