rootProject.name = "SuperTVStreamCast"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    // Precompiled script plugins shared via the tuntech_common_kmp submodule (validate-remote-config,
    // strip-ios-compose-resources).
    includeBuild("tuntech_common_kmp/build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

include(":androidApp")
include(":shared")
include(":common")
project(":common").projectDir = file("tuntech_common_kmp")
include(":monetization")
project(":monetization").projectDir = file("tuntech_monetization_kmp")
include(":mmp")
project(":mmp").projectDir = file("tuntech_mmp_kmp")
include(":mmp_firebase")
project(":mmp_firebase").projectDir = file("tuntech_mmp_firebase_kmp")
