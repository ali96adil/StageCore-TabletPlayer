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
        versionCode = 100
        versionName = "1.0.0-rc1"
        buildConfigField("String", "BUILD_LABEL", "\"V1 RC1 production polish\"")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
