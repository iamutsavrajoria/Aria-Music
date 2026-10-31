// SPDX-License-Identifier: GPL-3.0-only
import java.util.Properties

plugins {
    id("com.android.application")
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.ksp)
}

// aria.properties is the single source of the app name and application ID.
val brand = Properties().apply { file("../aria.properties").inputStream().use { load(it) } }

android {
    namespace = "app.aria.music"
    compileSdk = 37
    defaultConfig {
        applicationId = brand.getProperty("app.id")
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        resValue("string", "app_name", brand.getProperty("app.name"))
        buildConfigField("String", "BRAND_NAME", "\"${brand.getProperty("app.name")}\"")
    }
    // AGP 8+ disables generated resource values by default; resValue() above needs this.
    buildFeatures { compose = true; buildConfig = true; resValues = true }
    flavorDimensions += listOf("variant")
    productFlavors {
        create("foss") { dimension = "variant" }
        create("gms") { dimension = "variant" }
    }
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a")
            isUniversalApk = true
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug { applicationIdSuffix = ".debug" }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
        isCoreLibraryDesugaringEnabled = true // required by :innertube
    }
}

kotlin { jvmToolchain(21) }

dependencies {
    coreLibraryDesugaring(libs.desugaring)
    implementation(project(":domain"))
    implementation(project(":core"))
    implementation(project(":data"))
    implementation(project(":designsystem"))
    implementation(project(":innertube"))
    implementation(project(":playback"))
    implementation(libs.hilt)
    ksp(libs.hilt.compiler)
    implementation(libs.activity)
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.material3)
    implementation(libs.coroutines.core)
    implementation(libs.room.runtime)
    implementation(libs.timber)
}
