plugins {
    id("com.android.application")
}

android {
    namespace = "com.stagecore.player"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.stagecore.player"
        minSdk = 26
        targetSdk = 35
        versionCode = 101
        versionName = "1.0.0-rc2"
        buildConfigField("String", "BUILD_LABEL", "\"V1 RC2 stable owner credit — © 2026 Ali Adil — ali96adil@gmail.com — All rights reserved\"")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}