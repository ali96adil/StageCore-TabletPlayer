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
        versionCode = 102
        versionName = "1.0.0-rc3"
        buildConfigField("String", "BUILD_LABEL", "\"© 2026 Ali Adil — ali96adil@gmail.com — All rights reserved\"")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation("junit:junit:4.13.2")
}
