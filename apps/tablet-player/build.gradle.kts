plugins {
    id("com.android.application")
}

val stageCoreBuildRevision =
    System.getenv("STAGECORE_BUILD_SHA")?.trim()?.takeIf { it.isNotEmpty() } ?: "local"
val stageCoreBuildRevisionShort =
    if (stageCoreBuildRevision.length > 12) stageCoreBuildRevision.take(12) else stageCoreBuildRevision
val stageCoreDebugKeystore =
    System.getenv("STAGECORE_DEBUG_KEYSTORE")?.trim()?.takeIf { it.isNotEmpty() }

android {
    namespace = "com.stagecore.player"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.stagecore.player"
        minSdk = 26
        targetSdk = 35
        versionCode = 104
        versionName = "1.0.0-rc5"
        buildConfigField("String", "BUILD_LABEL", "\"© 2026 Ali Adil — ali96adil@gmail.com — All rights reserved\"")
        buildConfigField("String", "BUILD_REVISION", "\"$stageCoreBuildRevision\"")
    }

    signingConfigs {
        getByName("debug") {
            if (stageCoreDebugKeystore != null) {
                storeFile = file(stageCoreDebugKeystore)
                storePassword = "android"
                keyAlias = "stagecoredebug"
                keyPassword = "android"
            }
        }
    }

    // Isolate the experimental MJPEG APK from the installed RC3 release.
    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".mjpegtrial"
            versionNameSuffix = "-mjpegtrial-$stageCoreBuildRevisionShort"
        }
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
