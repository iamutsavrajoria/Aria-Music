// SPDX-License-Identifier: GPL-3.0-only
plugins { id("org.jetbrains.kotlin.jvm") }

kotlin { jvmToolchain(21) }

tasks.withType<Test> { useJUnit() }

dependencies {
    api(libs.coroutines.core)
    testImplementation(kotlin("test"))
}
