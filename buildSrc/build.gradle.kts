// SPDX-License-Identifier: GPL-3.0-only
plugins { `kotlin-dsl` }

repositories { mavenCentral() }

dependencies { testImplementation("junit:junit:4.13.2") }

kotlin { jvmToolchain(21) }

tasks.withType<Test> { useJUnit() }
