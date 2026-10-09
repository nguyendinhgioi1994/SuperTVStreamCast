@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    // remote_config_defaults.json validator (tuntech_common_kmp/build-logic precompiled plugin).
    id("validate-remote-config")
    // Strips duplicated iOS Compose resource folders (tuntech_common_kmp/build-logic precompiled plugin).
    id("strip-ios-compose-resources")
}

// SwiftPM-import namespace is `swiftPMImport.<group>.<project.name>` → `swiftPMImport.tuntech.shared`.
group = "tuntech"

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    // Library target only. The Android application (applicationId, google-services, MainActivity /
    // MainApplication, launcher assets) lives in :androidApp.
    android {
        namespace = "com.tuntech.supertvstreamcast.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
        androidResources {
            enable = true
        }
        withHostTest {
            isIncludeAndroidResources = true
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.appcompat)
            implementation(libs.koin.android)
            implementation(libs.ktor.client.okhttp)
            // IPTV player: HLS / MPEG-TS playback and the stock player view.
            implementation(libs.androidx.media3.exoplayer)
            implementation(libs.androidx.media3.exoplayer.hls)
            implementation(libs.androidx.media3.ui)
            // Chromecast hand-over for IPTV streams (Default Media Receiver).
            implementation(libs.androidx.media3.cast)
            implementation(libs.compose.ui.tooling.preview)
            implementation(project.dependencies.platform(libs.firebase.bom))
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.ui.tooling.preview)

            implementation(libs.kotlinx.serialization.json)
            // Local programme times for the TV guide (:common's DateUtil takes kotlinx-datetime types).
            implementation(libs.kotlinx.datetime)
            implementation(libs.ktor.client.core)

            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)

            implementation(libs.kermit)
            implementation(libs.kermit.crashlytics)

            // Firebase (GitLive)
            implementation(libs.gitlive.firebase.config)
            implementation(libs.gitlive.firebase.analytics)
            implementation(libs.gitlive.firebase.crashlytics)

            implementation(libs.androidx.navigation3.ui)
            implementation(libs.androidx.lifecycle.viewmodel.navigation3)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.androidx.datastore)
            implementation(libs.androidx.datastore.preferences)

            // :common's PlatformContext is coil's; FileKit resolves the DataStore directory.
            implementation(libs.coil.compose)
            // Channel logos from playlist URLs.
            implementation(libs.coil.network.ktor3)
            implementation(libs.filekit.core)
            // IPTV: QR code of a share link, and scanning one with the camera.
            implementation(libs.qrose)
            implementation(libs.camerak)
            implementation(libs.camerak.qr.scanner)
            // Camera permission prompt for the QR scanner.
            implementation(libs.moko.permissions)
            implementation(libs.moko.permissions.compose)
            implementation(libs.moko.permissions.camera)

            implementation(project(":common"))
            implementation(project(":monetization"))
            implementation(project(":mmp"))
            implementation(project(":mmp_firebase"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutines.test)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "shared.resources"
    generateResClass = always
}

dependencies {
    androidRuntimeClasspath(libs.compose.ui.tooling)
}
